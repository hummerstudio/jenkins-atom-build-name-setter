package atom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.Json;
import com.jenkinschina.atom.sdk.testing.Atom;
import com.jenkinschina.atom.sdk.testing.AtomResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The build-name-setter atom, started the way the runner starts it.
 *
 * <p>These are the checks the old {@code tools/smoke.sh} made: the template path, the log-regex path, and the case
 * where the task forgot to declare the log. That last one must fail with a readable message instead of silently
 * keeping the template.
 */
class BuildNameSetterIT {

    private Atom atom;

    @BeforeEach
    void setUp() throws Exception {
        atom = Atom.of();
    }

    private static Map<String, Object> mutations(AtomResult run) {
        String file = run.file("mutations.json");
        assertTrue(file != null, "the atom wrote no mutations.json\nconsole:\n" + run.console());
        return Json.parseObject(file);
    }

    @Test
    @DisplayName("a template is expanded from the build environment and the description is set")
    void expandsTheTemplate() throws Exception {
        AtomResult run = atom.input("BUILD_NUMBER", "7")
                .input("GIT_COMMIT", "abc1234")
                .input("{\"displayName\": \"release-${BUILD_NUMBER}-${GIT_COMMIT}\","
                        + " \"description\": \"built by atom\", \"fromLog\": \"\", \"matchTemplate\": \"\"}")
                .run();

        run.assertOk();
        Map<String, Object> mutations = mutations(run);
        assertEquals("release-7-abc1234", mutations.get("displayName"));
        assertEquals("built by atom", mutations.get("description"));
        assertEquals("success", run.status());
        assertTrue(run.console().contains("[atom] task com.jenkinschina.atom"),
                "the SDK did not run the task\nconsole:\n" + run.console());
    }

    @Test
    @DisplayName("the name comes from the console log, with the captured group wrapped in the template")
    void takesTheNameFromTheLog() throws Exception {
        AtomResult run = atom.file("build.log", "Checking out\nartifact: mylib-2.4.1.jar\npacked\n")
                .input("{\"displayName\": \"unknown\", \"description\": \"\","
                        + " \"fromLog\": \"artifact: mylib-([0-9.]+)\\\\.jar\", \"matchTemplate\": \"v%MATCH%\","
                        + " \"atom_build_log\": \"build.log\"}")
                .run();

        run.assertOk();
        Map<String, Object> mutations = mutations(run);
        assertEquals("v2.4.1", mutations.get("displayName"));
        assertFalse(mutations.containsKey("description"), "an empty description must not be written");
    }

    @Test
    @DisplayName("fromLog without the log granted fails loudly, it does not keep the template")
    void refusesAMissingLogContext() throws Exception {
        AtomResult run = atom.input("{\"displayName\": \"x\", \"description\": \"\","
                        + " \"fromLog\": \"nothing\", \"matchTemplate\": \"\"}")
                .run();

        run.assertRefused();
        assertEquals("error", run.status());
        assertTrue(run.file("output.json").contains("build log was not handed over"),
                "message is not readable:\n" + run.file("output.json"));
    }
}
