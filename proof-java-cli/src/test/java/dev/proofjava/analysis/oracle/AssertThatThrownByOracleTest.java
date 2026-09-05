package dev.proofjava.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;

import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * D-94: {@code assertThatThrownBy}/{@code thenThrownBy} are self-contained
 * oracles - their own body already runs {@code hasBeenThrown()}
 * ({@code @CanIgnoreReturnValue} in AssertJ's own source) - unlike every
 * other {@code assertThatXxx}/{@code thenXxx} chain-anchor, which needs a
 * terminal call chained onto it to assert anything. Found on assertj's own
 * test suite (campaign/assertj/run-log.md): used unchained, the old
 * prefix-only {@code isChainAnchor} match silently dropped a real oracle.
 */
class AssertThatThrownByOracleTest {

    private static final Path HARNESS_DIR = Path.of("target/fixture-harness");

    @TempDir
    Path repoRoot;

    private ModuleDefinition module() {
        return new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));
    }

    private Path testDir() throws IOException {
        Path dir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(dir);
        return dir;
    }

    private static List<TypeSolver> assertjJar() {
        try {
            return List.of(new JarTypeSolver(HARNESS_DIR.resolve("assertj-core.jar")));
        } catch (IOException e) {
            throw new IllegalStateException("fixture-harness jars missing - run 'mvn generate-test-resources' first", e);
        }
    }

    private OracleScanResult scan() {
        return OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults().withTypeSolvers(assertjJar()));
    }

    @Test
    void unchainedAssertThatThrownByIsRecognizedAsAnOracle() throws IOException {
        Files.writeString(testDir().resolve("ThrowsTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.assertj.core.api.Assertions.assertThatThrownBy;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ThrowsTest {",
            "    @Test",
            "    void throwsWhenGivenNull() {",
            "        assertThatThrownBy(() -> { throw new IllegalArgumentException(); });",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    @Test
    void chainedAssertThatThrownByIsStillRecognized() throws IOException {
        Files.writeString(testDir().resolve("ThrowsChainedTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.assertj.core.api.Assertions.assertThatThrownBy;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ThrowsChainedTest {",
            "    @Test",
            "    void throwsWithMessage() {",
            "        assertThatThrownBy(() -> { throw new IllegalArgumentException(\"bad\"); })",
            "            .hasMessage(\"bad\");",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    @Test
    void unchainedThenThrownByIsRecognizedAsAnOracle() throws IOException {
        Files.writeString(testDir().resolve("BddThrowsTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.assertj.core.api.BDDAssertions.thenThrownBy;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class BddThrowsTest {",
            "    @Test",
            "    void throwsWhenGivenNull() {",
            "        thenThrownBy(() -> { throw new IllegalArgumentException(); });",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    /**
     * The negative control: unlike assertThatThrownBy, assertThatCode's body
     * does nothing but return a plain assert object - it genuinely still
     * needs something chained to assert anything, and must stay flagged when
     * nothing is.
     */
    @Test
    void unchainedAssertThatCodeIsStillFlaggedAsNoRecognizedOracle() throws IOException {
        Files.writeString(testDir().resolve("CodeTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.assertj.core.api.Assertions.assertThatCode;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class CodeTest {",
            "    @Test",
            "    void doesNothingUseful() {",
            "        assertThatCode(() -> { });",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("NO_RECOGNIZED_ORACLE", result.findings().get(0).rule());
    }
}
