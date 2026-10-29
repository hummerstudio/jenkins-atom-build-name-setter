package com.jenkinschina.atom.buildnamesetter;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * build-name-setter as an atom, written against the SDK.
 *
 * <p>The SDK does the contract: it reads {@code input.json}, hands over typed parameters and secrets, and writes
 * {@code output.json} for us. What is left here is the plugin's actual behaviour — expand {@code ${ENV}} in the
 * templates, optionally take the name from a regex over the build log, and ask the runner to rename the build.
 */
public class BuildNameSetter implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        String displayName = expand(context, context.getParam("displayName", "#${BUILD_NUMBER}"));
        String description = expand(context, context.getParam("description", ""));
        String fromLog = context.getParam("fromLog", "");
        String matchTemplate = context.getParam("matchTemplate", "");

        if (!fromLog.isBlank()) {
            // the runner copies the environment into input.json; the env fallback keeps a local run working
            String logName = context.getParam("atom_build_log", System.getenv().getOrDefault("atom_build_log", ""));
            if (logName.isBlank()) {
                return AtomResult.error(
                        ErrorType.USER,
                        40001,
                        "fromLog is set but task.json does not declare x-jenkins.context [log]");
            }
            Path logFile = context.getDataDir().resolve(logName);
            if (!Files.isRegularFile(logFile)) {
                return AtomResult.error(ErrorType.USER, 40002, "build log not found: " + logName);
            }
            String log = Files.readString(logFile, StandardCharsets.UTF_8);
            Matcher matcher;
            try {
                matcher = Pattern.compile(fromLog, Pattern.MULTILINE).matcher(log);
            } catch (RuntimeException e) {
                return AtomResult.error(ErrorType.USER, 40003, "fromLog is not a valid regular expression: " + e);
            }
            if (matcher.find()) {
                String matched = matcher.groupCount() >= 1 && matcher.group(1) != null
                        ? matcher.group(1)
                        : matcher.group();
                displayName = expand(
                        context, matchTemplate.isBlank() ? matched : matchTemplate.replace("%MATCH%", matched));
            } else {
                context.log("[name-setter] no line matched " + fromLog + "; keeping the template");
            }
        }

        writeMutations(context, displayName, description);
        context.log("[name-setter] " + displayName);
        return AtomResult.success().data("displayName", displayName);
    }

    /**
     * The runner's mutation contract: a file next to the outputs, not an API call. Only these two fields are allowed,
     * which is why the atom cannot do anything else to the build.
     */
    private void writeMutations(AtomContext context, String displayName, String description) throws Exception {
        StringBuilder json = new StringBuilder("{");
        if (!displayName.isBlank()) {
            json.append("\"displayName\": \"").append(escape(displayName)).append('"');
        }
        if (!description.isBlank()) {
            if (json.length() > 1) {
                json.append(", ");
            }
            json.append("\"description\": \"").append(escape(description)).append('"');
        }
        json.append('}');
        Files.writeString(
                context.getDataDir().resolve("mutations.json"),
                json.toString(),
                StandardCharsets.UTF_8);
    }

    /** {@code ${BUILD_NUMBER}} and friends; an unknown name is left as written so the log shows what went wrong. */
    private static String expand(AtomContext context, String template) {
        Matcher matcher = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}").matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = context.getParam(matcher.group(1));
            if (value == null) {
                value = System.getenv(matcher.group(1));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
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
}
