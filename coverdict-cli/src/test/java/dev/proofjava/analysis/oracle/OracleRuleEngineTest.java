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
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * Covers the two OracleRuleEngine.scan branches the fixture-driven test
 * (OracleRuleEngineFixturesTest) cannot reach, because every fixture must
 * parse and no fixture run has 10,000+ findings: an unparseable test source
 * (M1c criterion 7) and the findings cap (SECURITY-POLICY.md #2).
 */
class OracleRuleEngineTest {

    @TempDir
    Path repoRoot;

    private ModuleDefinition module() {
        return new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));
    }

    @Test
    void anUnparseableTestSourceIsSkippedWithAStructuredReasonAndTheRunContinues() throws IOException {
        Path testDir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(testDir);
        // Deliberately broken syntax - an unterminated method body.
        Files.writeString(testDir.resolve("BrokenTest.java"), String.join("\n",
            "package com.example;",
            "import org.junit.jupiter.api.Test;",
            "class BrokenTest {",
            "    @Test",
            "    void broken( {",
            ""));
        Files.writeString(testDir.resolve("GoodTest.java"), noOracleTestSource("GoodTest", "noAssertionHere"));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.incompleteReasons().size());
        AnalysisReason reason = result.incompleteReasons().get(0);
        assertEquals("UNPARSEABLE_TEST_SOURCE", reason.code());
        assertEquals("src/test/java/com/example/BrokenTest.java", reason.path());

        assertEquals(1, result.findings().size(), "the broken file is skipped, not the whole run");
        assertEquals("src/test/java/com/example/GoodTest.java", result.findings().get(0).path());
    }

    @Test
    void findingsCapStopsAtAFileBoundaryAndRecordsTruncation() throws IOException {
        Path testDir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("ATest.java"), noOracleTestSource("ATest", "noAssertionHere"));
        Files.writeString(testDir.resolve("BTest.java"), noOracleTestSource("BTest", "noAssertionHere"));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults().withFindingsCap(1));

        assertEquals(1, result.findings().size(), "the cap is checked per file, not per finding - ATest's one finding is whole");
        Finding onlyFinding = result.findings().get(0);
        assertEquals("src/test/java/com/example/ATest.java", onlyFinding.path());

        assertTrue(result.incompleteReasons().stream().anyMatch(r -> "FINDINGS_TRUNCATED".equals(r.code())),
            result.incompleteReasons().toString());
    }

    @Test
    void crlfTestSourceProducesTheSameFindingAtTheSameLineAsLf() throws IOException {
        // M1c criterion 7: a CRLF-encoded Java file must parse to the same
        // AST positions as its LF counterpart - a naive line-splitter would
        // shift line numbers, but JavaParser's own line-ending handling
        // should not.
        Path testDir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(testDir);
        String lfSource = noOracleTestSource("CrlfTest", "noAssertionHere");
        Files.writeString(testDir.resolve("CrlfTest.java"), lfSource.replace("\n", "\r\n"));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(0, result.incompleteReasons().size(), result.incompleteReasons().toString());
        assertEquals(1, result.findings().size());
        Finding finding = result.findings().get(0);
        assertEquals("NO_RECOGNIZED_ORACLE", finding.rule());
        assertEquals(6, finding.startLine());
    }

    private static String noOracleTestSource(String className, String methodName) {
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
}
