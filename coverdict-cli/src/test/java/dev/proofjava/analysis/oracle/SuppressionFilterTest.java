package dev.proofjava.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ModuleDefinition;
import dev.proofjava.config.ProofConfig;

/**
 * {@code suppressions} through the real engine. The load-bearing assertion in
 * every case is not just that a finding disappeared from the list, but that
 * the {@code SUPPRESSED_FINDINGS} warning carries the count - a suppression
 * that silently shrank the array would be indistinguishable from the tests
 * actually getting better (hard rule 3a).
 */
class SuppressionFilterTest {

    @TempDir
    Path repoRoot;

    private ModuleDefinition module() {
        return new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));
    }

    private void writeTwoOracleLessTests() throws IOException {
        Path dir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("GeneratedClientTest.java"), oracleLess("GeneratedClientTest", "roundTrips"));
        Files.writeString(dir.resolve("HandWrittenTest.java"), oracleLess("HandWrittenTest", "alsoNoOracle"));
    }

    private static String oracleLess(String className, String methodName) {
        return String.join("\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class " + className + " {",
            "    @Test",
            "    void " + methodName + "() {",
            "        System.out.println(\"no assertion here\");",
            "    }",
            "}",
            "");
    }

    private static OracleScanOptions with(ProofConfig.Suppression... suppressions) {
        return OracleScanOptions.defaults().withConfig(new ProofConfig(
            null, null, null, null, List.of(), List.of(), List.of(suppressions)));
    }

    private static ProofConfig.Suppression suppression(String rule, String pathGlob, String methodPattern) {
        return new ProofConfig.Suppression(rule, pathGlob, methodPattern, "documented reason");
    }

    @Test
    void unsuppressedBaselineReportsBothFindingsAndNoWarning() throws IOException {
        writeTwoOracleLessTests();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(2, result.findings().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aPathGlobSuppressesOnlyMatchingFindingsAndTheCountIsReported() throws IOException {
        writeTwoOracleLessTests();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            with(suppression("NO_RECOGNIZED_ORACLE", "**/Generated*.java", null)));

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("HandWrittenTest.java", fileNameOf(result.findings().get(0).path()));

        assertEquals(1, result.warnings().size());
        AnalysisReason warning = result.warnings().get(0);
        assertEquals("SUPPRESSED_FINDINGS", warning.code());
        assertEquals(1, warning.count(), "the count must be machine-readable, not only prose");
    }

    @Test
    void aSuppressionForADifferentRuleDoesNotApply() throws IOException {
        writeTwoOracleLessTests();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            with(suppression("NULL_CHECK_ONLY", "**", null)));

        assertEquals(2, result.findings().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aTestMethodPatternNarrowsAPathWideSuppression() throws IOException {
        writeTwoOracleLessTests();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            with(suppression("NO_RECOGNIZED_ORACLE", "**", "*roundTrips*")));

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("HandWrittenTest.java", fileNameOf(result.findings().get(0).path()));
        assertEquals(1, result.warnings().get(0).count());
    }

    @Test
    void suppressingEverythingStillReportsHowMuchWasHidden() throws IOException {
        writeTwoOracleLessTests();

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            with(suppression("NO_RECOGNIZED_ORACLE", "**", null)));

        assertTrue(result.findings().isEmpty());
        assertEquals(2, result.warnings().get(0).count(),
            "an empty findings array must never be silently indistinguishable from a clean run");
    }

    private static String fileNameOf(String repoRelativePath) {
        return repoRelativePath.substring(repoRelativePath.lastIndexOf('/') + 1);
    }
}
