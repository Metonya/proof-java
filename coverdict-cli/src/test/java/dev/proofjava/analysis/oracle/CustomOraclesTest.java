package dev.proofjava.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * The {@code customOracles} configuration end to end through the real engine.
 *
 * <p>The scenario is the one every corpus phase kept labelling "D-17
 * territory" and marking out of scope: the test's real oracle lives in a
 * helper class in <em>another</em> compilation unit (gson's {@code
 * MoreAsserts}, assertj's {@code AssertionsUtil}, junit-framework's
 * {@code PreconditionAssertions}), so neither the built-in allowlist nor the
 * same-file helper traversal can see it, and the test is reported as having no
 * recognized oracle. Correct behaviour before this feature - but only
 * fixable once the user has a way to name the helper.
 */
class CustomOraclesTest {

    @TempDir
    Path repoRoot;

    private ModuleDefinition module() {
        return new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));
    }

    /** A helper in its own compilation unit, statically imported by the test - the real-world shape. */
    private void writeExternalHelperAndTest() throws IOException {
        Path dir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("MoreAsserts.java"), String.join("\n",
            "package com.example;",
            "",
            "public final class MoreAsserts {",
            "    public static void assertContainsRegex(String pattern, String actual) {",
            "        if (!actual.matches(pattern)) {",
            "            throw new AssertionError(actual);",
            "        }",
            "    }",
            "}",
            ""));
        Files.writeString(dir.resolve("RegexTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static com.example.MoreAsserts.assertContainsRegex;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class RegexTest {",
            "    @Test",
            "    void verifiesThroughAnExternalHelper() {",
            "        assertContainsRegex(\".*ok.*\", \"it is ok\");",
            "    }",
            "}",
            ""));
    }

    @Test
    void withoutConfigurationAnExternalHelperOracleIsStillReportedAsMissing() throws IOException {
        writeExternalHelperAndTest();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("NO_RECOGNIZED_ORACLE", result.findings().get(0).rule());
    }

    @Test
    void namingTheHelperInCustomOraclesRecognizesItAndTheFindingDisappears() throws IOException {
        writeExternalHelperAndTest();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults()
                .withConfig(config("com.example.MoreAsserts#assertContainsRegex")));

        assertTrue(result.findings().isEmpty(), "configured oracle should be recognized: " + result.findings());
    }

    @Test
    void aGlobInTheMethodPartMatches() throws IOException {
        writeExternalHelperAndTest();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults().withConfig(config("com.example.MoreAsserts#assert*")));

        assertTrue(result.findings().isEmpty(), result.findings().toString());
    }

    /** The glob covers the method name only - a different type with a matching method name is not an oracle. */
    @Test
    void anEntryForADifferentTypeDoesNotMatch() throws IOException {
        writeExternalHelperAndTest();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults().withConfig(config("com.other.MoreAsserts#assert*")));

        assertEquals(1, result.findings().size(), "a same-named type in another package must not match");
    }

    @Test
    void aMethodNameOutsideTheGlobDoesNotMatch() throws IOException {
        writeExternalHelperAndTest();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults().withConfig(config("com.example.MoreAsserts#verify*")));

        assertEquals(1, result.findings().size(), result.findings().toString());
    }

    private static dev.proofjava.config.CoverdictConfig config(String... customOracles) {
        return new dev.proofjava.config.CoverdictConfig(
            null, null, null, null, List.of(customOracles), List.of(), List.of());
    }
}
