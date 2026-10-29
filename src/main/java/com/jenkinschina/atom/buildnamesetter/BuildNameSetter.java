package com.jenkinschina.atom.buildnamesetter;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import com.jenkinschina.atom.sdk.Mutation;
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
            if (!context.hasBuildLog()) {
                return AtomResult.error(
                        ErrorType.USER,
                        40001,
                        "fromLog is set but the build log was not handed over"
                                + " (declare x-jenkins.context [\"log\"] in task.json)");
            }
            Matcher matcher;
            try {
                matcher = Pattern.compile(fromLog, Pattern.MULTILINE).matcher(context.buildLog());
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

        context.mutate(Mutation.of(
                displayName.isBlank() ? null : displayName,
                description.isBlank() ? null : description));
        context.log("[name-setter] " + displayName);
        return AtomResult.success().data("displayName", displayName);
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
}
