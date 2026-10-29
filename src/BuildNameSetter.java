import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ported build-name-setter: rename the running build, optionally from a regex over the console log.
 *
 * <p>The atom never talks to Jenkins. It reads {@code input.json} and the build log the runner handed it, and writes
 * {@code mutations.json}; the runner applies the change and prints it. Nothing else is required — no plugin, no API
 * client, no dependency.
 */
public class BuildNameSetter {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(System.getenv("atom_data_dir"));
        String inputName = value(System.getenv("atom_data_input"), "input.json");
        String outputName = value(System.getenv("atom_data_output"), "output.json");
        Map<String, String> env = System.getenv();
        String input = Files.readString(dir.resolve(inputName), StandardCharsets.UTF_8);

        String displayName = expand(text(input, "displayName", ""), env);
        String description = expand(text(input, "description", ""), env);
        String fromLog = text(input, "fromLog", "");
        String matchTemplate = text(input, "matchTemplate", "");

        if (!fromLog.isBlank()) {
            String logName = System.getenv("atom_build_log");
            if (logName == null || logName.isBlank()) {
                fail(dir, outputName, "fromLog is set but task.json does not declare x-jenkins.context [log]");
                return;
            }
            String log = Files.readString(dir.resolve(logName), StandardCharsets.UTF_8);
            Matcher matcher;
            try {
                matcher = Pattern.compile(fromLog, Pattern.MULTILINE).matcher(log);
            } catch (RuntimeException e) {
                fail(dir, outputName, "fromLog is not a valid regular expression: " + e.getMessage());
                return;
            }
            if (matcher.find()) {
                String matched = matcher.groupCount() >= 1 && matcher.group(1) != null
                        ? matcher.group(1)
                        : matcher.group();
                displayName = expand(
                        matchTemplate.isBlank() ? matched : matchTemplate.replace("%MATCH%", matched), env);
            } else {
                System.out.println("[name-setter] no line matched " + fromLog + "; keeping the template");
            }
        }

        StringBuilder mutations = new StringBuilder("{");
        if (!displayName.isBlank()) {
            mutations.append("\"displayName\": \"").append(escape(displayName)).append('"');
        }
        if (!description.isBlank()) {
            if (mutations.length() > 1) {
                mutations.append(", ");
            }
            mutations.append("\"description\": \"").append(escape(description)).append('"');
        }
        mutations.append('}');
        Files.writeString(dir.resolve("mutations.json"), mutations.toString(), StandardCharsets.UTF_8);

        String outputs = "{\"status\":\"success\",\"data\":{\"displayName\":{\"type\":\"string\",\"value\":\""
                + escape(displayName) + "\"}}}";
        Files.writeString(dir.resolve(outputName), outputs, StandardCharsets.UTF_8);
        System.out.println("[name-setter] " + displayName);
    }

    private static void fail(Path dir, String outputName, String message) throws Exception {
        Files.writeString(
                dir.resolve(outputName),
                "{\"status\":\"error\",\"errorType\":1,\"errorCode\":40001,\"message\":\"" + escape(message) + "\"}",
                StandardCharsets.UTF_8);
        System.out.println("[name-setter] ERROR " + message);
    }

    /** {@code ${BUILD_NUMBER}} and friends; an unknown name is left untouched so the log shows what went wrong. */
    private static String expand(String template, Map<String, String> env) {
        Matcher matcher = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}").matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = env.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Reads a string field out of input.json without a JSON dependency: the atom only reads what it declared. */
    private static String text(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return unescape(matcher.group(1));
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
