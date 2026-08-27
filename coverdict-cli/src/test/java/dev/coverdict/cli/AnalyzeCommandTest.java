package dev.coverdict.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

import picocli.CommandLine;

/** Runs the real fixtures end to end through the picocli-wired command, the way a user actually invokes it. */
class AnalyzeCommandTest {

    // Absolute: --report paths are resolved against --repo (a @TempDir here,
    // unrelated to this module's own basedir), so a relative fixture path
    // would be looked up in the wrong place.
    private static final Path FIXTURES = Path.of("../fixtures/jacoco").toAbsolutePath();
    private static final Path SCHEMA_FILE = Path.of("../schema/coverdict-verdict.schema.json");

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @TempDir
    Path repoRoot;

    // Separate from repoRoot: writing --out inside the repository under test
    // would make coverdict's own verdict file an untracked path that the
    // NEXT run's diff sees, which is a real self-referential effect but not
    // one these tests are about - keep verdict output outside the repo.
    @TempDir
    Path outputDir;

    private int run(String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute(args);
    }

    private Set<ValidationMessage> validate(Path jsonFile) throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012);
        JsonSchema schema;
        try (var in = Files.newInputStream(SCHEMA_FILE)) {
            schema = factory.getSchema(in);
        }
        JsonNode node = new ObjectMapper().readTree(Files.readAllBytes(jsonFile));
        return schema.validate(node);
    }

    @Test
    void singleModuleShorthandProducesASchemaValidCompleteVerdict() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(Files.exists(outFile));
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("complete", doc.at("/analysis/status").asText());
        assertEquals(80.0, doc.at("/coverage/overall/jacoco-line/percent").asDouble());
        assertEquals("unavailable_no_vcs", doc.at("/coverage/newCode/status").asText());
        assertEquals("root", doc.at("/inputs/modules/0/id").asText());
    }

    @Test
    void unicodeAndSpacePathResolvesEndToEndWithNoMissingSourceFileWarning() throws IOException {
        // Distinct from the unit-level ModuleBinderTest coverage of the same
        // fixture: that test never writes the file to disk, so only the
        // foundOnDisk=false branch is exercised there. Here the file is real
        // and the run goes through the full CLI (M1c criterion 7). The path
        // itself is not part of the no-vcs JSON shape (no per-file
        // breakdown outside changedFiles, which is diff-mode only) - the
        // observable effect of foundOnDisk=false would be a
        // MISSING_SOURCE_FILE warning, so its absence is the proof.
        Files.createDirectories(repoRoot.resolve("src/main/java/com/exämple/wëird pkg"));
        Files.writeString(repoRoot.resolve("src/main/java/com/exämple/wëird pkg/Ünïcödé File.java"),
            "package com.exämple;\nclass A {}\n", StandardCharsets.UTF_8);
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("unicode-and-spaces.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertTrue(doc.at("/warnings").isEmpty(), doc.toPrettyString());
        assertEquals(50.0, doc.at("/coverage/overall/jacoco-line/percent").asDouble());
    }

    @Test
    void aReportPathGivenWithBackslashesIsNormalizedToForwardSlashesInTheJson() throws IOException {
        // The schema's $defs/path pattern rejects backslashes outright; a
        // Windows-style --report argument must not leak one into the JSON
        // (D-22, M1c criterion 7). Built by hand rather than relying on
        // Path#toString, whose separator depends on the OS this test happens
        // to run on.
        String backslashArg = FIXTURES.toString().replace('/', '\\') + "\\mixed-coverage.xml";
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", backslashArg,
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        String reportPath = doc.at("/inputs/modules/0/reports/0/path").asText();
        assertTrue(reportPath.endsWith("mixed-coverage.xml"), reportPath);
        assertFalse(reportPath.contains("\\"), reportPath);
    }

    @Test
    void malformedReportProducesASchemaValidIncompleteVerdictAndExitThree() throws IOException {
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("malformed.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(Files.exists(outFile), "hard rule 3a: an incomplete analysis still writes a structured document");
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("incomplete", doc.at("/analysis/status").asText());
        assertEquals("MALFORMED_JACOCO_XML", doc.at("/analysis/incompleteReasons/0/code").asText());
    }

    @Test
    void bareReportCombinedWithModuleIsInvalidInvocationAndWritesNoJson() {
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "demo=.",
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(), // bare, no id=
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertFalse(Files.exists(outFile), "exit 2 must never write a verdict document (schema note)");
    }

    @Test
    void malformedModuleOptionIsInvalidInvocationAndWritesNoJson() {
        // Missing "id=", e.g. a user forgetting the "=" in --module id=dir.
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "no-equals-sign-here",
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertFalse(Files.exists(outFile), "exit 2 must never write a verdict document (schema note)");
    }

    @Test
    void duplicateModuleIdIsInvalidInvocationAndWritesNoJson() {
        // hard rule 3a: which of the two roots wins must never be a silent guess.
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "demo=.",
            "--module", "demo=other",
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertFalse(Files.exists(outFile), "exit 2 must never write a verdict document (schema note)");
    }

    @Test
    void duplicateSourceRootsIdIsInvalidInvocation() {
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "demo=.",
            "--source-roots", "demo=src/main/java",
            "--source-roots", "demo=src/other",
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
    }

    @Test
    void repeatingTheSameModuleIdAcrossMultipleReportOptionsIsStillValid() {
        // --report has its own parser and legitimately allows multiple
        // reports per module id - the duplicate-id rejection above must not
        // regress this.
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "demo=.",
            "--report", "demo=" + FIXTURES.resolve("duplicate-a.xml"),
            "--report", "demo=" + FIXTURES.resolve("duplicate-b.xml"),
            "--out", outFile.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode, "duplicate class identity across the two reports is a real, separate rejection (D-16)");
        assertTrue(Files.exists(outFile));
    }

    @Test
    void unwritableOutPathIsInternalErrorNotACrash() {
        // Parent directory does not exist and is never created for --out.
        Path outFile = repoRoot.resolve("does/not/exist/verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INTERNAL_ERROR.value(), exitCode);
        assertTrue(err.toString().contains("could not write"), err.toString());
    }

    @Test
    void zeroDiffModesIsInvalidInvocation() {
        int exitCode = run("analyze", "--report", FIXTURES.resolve("mixed-coverage.xml").toString());
        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
    }

    @Test
    void multipleDiffModesIsInvalidInvocationAndWritesNoJson() {
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs", "--uncommitted",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertFalse(Files.exists(outFile));
    }

    @Test
    void uncommittedModeProducesRealNewCodeCoverageForAChangedMappedLine() throws IOException, InterruptedException {
        initGitRepo(repoRoot);
        Path calc = repoRoot.resolve("src/main/java/com/example/Calc.java");
        Files.createDirectories(calc.getParent());
        // Lines 10-14 match fixtures/jacoco/mixed-coverage.xml's own <line> entries exactly.
        Files.writeString(calc, calcJavaBody("int c = 3;"));
        commitAll(repoRoot, "base");
        Files.writeString(calc, calcJavaBody("int c = 30;")); // uncommitted edit on line 12 (ci=1, mi=2: jacoco-line covered)

        Path outFile = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--uncommitted",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("working-tree", doc.at("/inputs/diffMode").asText());
        assertTrue(doc.at("/inputs/resolved/head").asText().matches("[0-9a-f]{40}"));
        assertTrue(doc.at("/inputs/resolved/dirty").asBoolean());
        assertEquals(1, doc.at("/coverage/newCode/jacoco-line/numerator").asInt());
        assertEquals(1, doc.at("/coverage/newCode/jacoco-line/denominator").asInt());
        assertEquals("mapped", doc.at("/changedFiles/0/classification").asText());
        assertEquals(1, doc.at("/changedFiles/0/newLines").asInt());
    }

    @Test
    void baseRefModeRecordsResolvedIdentityAndIncludesUncommittedEdits() throws IOException, InterruptedException {
        initGitRepo(repoRoot);
        Path calc = repoRoot.resolve("src/main/java/com/example/Calc.java");
        Files.createDirectories(calc.getParent());
        Files.writeString(calc, calcJavaBody("int c = 3;"));
        commitAll(repoRoot, "base");
        String mainSha = runGit(repoRoot, "rev-parse", "HEAD").strip();
        runGit(repoRoot, "checkout", "-q", "-b", "feature");
        Files.writeString(calc, calcJavaBody("int c = 3; // touched on feature"));
        commitAll(repoRoot, "feature work");
        String featureSha = runGit(repoRoot, "rev-parse", "HEAD").strip();
        Files.writeString(calc, calcJavaBody("int c = 30;")); // uncommitted on top of the feature commit

        Path outFile = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--base", "main",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("base-ref", doc.at("/inputs/diffMode").asText());
        assertEquals("main", doc.at("/inputs/baseRef").asText());
        assertEquals(mainSha, doc.at("/inputs/resolved/base").asText());
        assertEquals(mainSha, doc.at("/inputs/resolved/mergeBase").asText());
        assertEquals(featureSha, doc.at("/inputs/resolved/head").asText());
        assertTrue(doc.at("/inputs/resolved/dirty").asBoolean());
        assertEquals(1, doc.at("/coverage/newCode/jacoco-line/denominator").asInt());
    }

    @Test
    void unresolvableBaseRefIsIncompleteButKeepsTheAlreadyComputedOverallCoverage() throws IOException, InterruptedException {
        initGitRepo(repoRoot);
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        commitAll(repoRoot, "base");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--base", "no-such-ref",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("incomplete", doc.at("/analysis/status").asText());
        assertEquals("UNRESOLVABLE_REF", doc.at("/analysis/incompleteReasons/0/code").asText());
        assertEquals("unavailable_incomplete", doc.at("/coverage/newCode/status").asText());
        // D-26: the overall-coverage phase ran to completion before the diff acquisition
        // failed, so its real numbers are preserved rather than zeroed out.
        assertEquals(80.0, doc.at("/coverage/overall/jacoco-line/percent").asDouble());
    }

    @Test
    void missingMergeBaseIsIncompleteButKeepsTheAlreadyComputedOverallCoverage() throws IOException, InterruptedException {
        // A real, resolvable ref that shares no history with the current
        // branch (M1c criterion 7) - distinct from unresolvableBaseRef above,
        // which never finds the ref at all.
        initGitRepo(repoRoot);
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        commitAll(repoRoot, "base");
        runGit(repoRoot, "checkout", "-q", "--orphan", "unrelated");
        commitAll(repoRoot, "unrelated root");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--base", "main",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("incomplete", doc.at("/analysis/status").asText());
        assertEquals("MISSING_MERGE_BASE", doc.at("/analysis/incompleteReasons/0/code").asText());
        assertEquals("unavailable_incomplete", doc.at("/coverage/newCode/status").asText());
        assertEquals(80.0, doc.at("/coverage/overall/jacoco-line/percent").asDouble());
    }

    /**
     * The other half of {@code analyze}'s {@code catch (AnalysisException)}
     * branch: with {@code --findings-scope changed}, a failed diff means there
     * is no changed-file set to scan test sources against, so the oracle scan
     * is skipped entirely rather than re-run over "no files" - a real,
     * reachable branch (not dead code) that no prior test happened to select,
     * since every other AnalysisException test here uses the {@code all}
     * default.
     */
    @Test
    void aFailedDiffWithFindingsScopeChangedSkipsTheOracleScanEntirely() throws IOException, InterruptedException {
        initGitRepo(repoRoot);
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"),
            noOracleTestSource("CalcTest", "noAssertionHere"));
        commitAll(repoRoot, "base");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--base", "no-such-ref", "--findings-scope", "changed",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("incomplete", doc.at("/analysis/status").asText());
        assertEquals("UNRESOLVABLE_REF", doc.at("/analysis/incompleteReasons/0/code").asText());
        // The real oracle-less test above is never even looked at: no second
        // UNPARSEABLE_TEST_SOURCE/finding-related reason, and findings is
        // empty rather than reflecting a scan that didn't run.
        assertEquals(1, doc.at("/analysis/incompleteReasons").size());
        assertTrue(doc.at("/findings").isEmpty(), doc.at("/findings").toString());
    }

    @Test
    void diffModeRunsAreByteIdenticalAcrossTwoInvocations() throws IOException, InterruptedException {
        initGitRepo(repoRoot);
        Path calc = repoRoot.resolve("src/main/java/com/example/Calc.java");
        Files.createDirectories(calc.getParent());
        Files.writeString(calc, calcJavaBody("int c = 3;"));
        commitAll(repoRoot, "base");
        Files.writeString(calc, calcJavaBody("int c = 30;"));

        Path first = outputDir.resolve("first.json");
        Path second = outputDir.resolve("second.json");
        run("analyze", "--uncommitted", "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(), "--out", first.toString());
        run("analyze", "--uncommitted", "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(), "--out", second.toString());

        // Byte comparison, not String - a BOM or encoding difference between
        // the two runs must not slip through a String-level equals (M1c criterion 1).
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void noVcsModeScansAllTestSourcesByDefaultAndProducesARealOracleFinding() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), noOracleTestSource("CalcTest", "noAssertionHere"));
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode, "a finding is not an incomplete reason - ROADMAP: exit 0 regardless of findings");
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("all", doc.at("/inputs/findingsScope").asText());
        assertEquals(1, doc.at("/findings").size());
        assertEquals("NO_RECOGNIZED_ORACLE", doc.at("/findings/0/rule").asText());
        assertEquals("HIGH", doc.at("/findings/0/confidence").asText());
        assertEquals("src/test/java/com/example/CalcTest.java", doc.at("/findings/0/path").asText());
    }

    @Test
    void findingsScopeChangedWithNoVcsIsInvalidInvocationAndWritesNoJson() {
        Path outFile = repoRoot.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs", "--findings-scope", "changed",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertFalse(Files.exists(outFile));
    }

    @Test
    void invalidFindingsScopeValueIsInvalidInvocation() {
        int exitCode = run("analyze", "--no-vcs", "--findings-scope", "bogus",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString());
        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
    }

    @Test
    void findingsScopeChangedOnlyScansTestFilesTouchedByTheDiff() throws IOException, InterruptedException {
        initGitRepo(repoRoot);
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/OldTest.java"), noOracleTestSource("OldTest", "oldNoAssertion"));
        commitAll(repoRoot, "base");
        // Untracked new test file - untouched OldTest.java must not be scanned in "changed" scope.
        Files.writeString(repoRoot.resolve("src/test/java/com/example/NewTest.java"), noOracleTestSource("NewTest", "newNoAssertion"));

        Path outFile = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--uncommitted", "--findings-scope", "changed",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("changed", doc.at("/inputs/findingsScope").asText());
        assertEquals(1, doc.at("/findings").size());
        assertEquals("src/test/java/com/example/NewTest.java", doc.at("/findings/0/path").asText());
    }

    @Test
    void moduleWithoutAnyReportIsExcludedWithAWarningNotAnError() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "used=.",
            "--module", "unused=.",
            "--report", "used=" + FIXTURES.resolve("mixed-coverage.xml"),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals(1, doc.at("/inputs/modules").size(), "module without a report must not appear as evidence");
        boolean warned = false;
        for (JsonNode w : doc.at("/warnings")) {
            if (w.at("/code").asText().equals("MODULE_WITHOUT_REPORT")) {
                warned = true;
            }
        }
        assertTrue(warned, doc.toString());
    }

    // --- --classpath (M0-CLI-INPUT.md's classpath input, wired for real) ---

    /** Same validation shape for all four id-keyed list/target flags (SonarQube java:S5976). */
    @ParameterizedTest
    @ValueSource(strings = {"--classpath", "--per-test-classpath", "--mutation-classpath", "--mutation-target"})
    void classpathIdNotMatchingAnyDeclaredModuleIsInvalidInvocation(String flag) {
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "app=.",
            "--report", "app=" + FIXTURES.resolve("mixed-coverage.xml"),
            flag, "typo=deps.txt",
            "--out", outputDir.resolve("verdict.json").toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertTrue(err.toString().contains(flag + " id 'typo'"), err.toString());
        assertFalse(Files.exists(outputDir.resolve("verdict.json")), "exit 2 must write no JSON");
    }

    // --- --per-test-report / --per-test-classpath (D-55) ---

    @Test
    void perTestReportWithNoVcsIsInvalidInvocationAndWritesNoJson() {
        Path outFile = repoRoot.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs", "--per-test-report",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertTrue(err.toString().contains("--per-test-report"), err.toString());
        assertFalse(Files.exists(outFile));
    }

    @Test
    void withoutPerTestReportTheOutputHasNoPerTestFieldAtAll() throws IOException {
        Path outFile = repoRoot.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertTrue(doc.at("/perTest").isMissingNode(), "perTest must be entirely absent, not null, when the flag is off");
    }

    // --- --mutation-report / --mutation-classpath (D-56) ---

    @Test
    void mutationReportWithNoVcsIsInvalidInvocationAndWritesNoJson() {
        Path outFile = repoRoot.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs", "--mutation-report",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertTrue(err.toString().contains("--mutation-report"), err.toString());
        assertFalse(Files.exists(outFile));
    }

    // --- --mutation-target (Plan.md Faz 2) ---

    @Test
    void mutationTargetWithoutMutationReportIsInvalidInvocationAndWritesNoJson() {
        Path outFile = repoRoot.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs", "--mutation-target", "root=com.example.Calc",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertTrue(err.toString().contains("--mutation-target requires --mutation-report"), err.toString());
        assertFalse(Files.exists(outFile));
    }

    /**
     * The carve-out this whole flag exists for: {@code --mutation-report}
     * alone is rejected under {@code --no-vcs} (the test just above this
     * section), but adding a real {@code --mutation-target} lifts that -
     * proven here without a real PIT run by naming a class that does not
     * exist, so target resolution fails fast with a warning instead of
     * spawning the engine (the real-PIT, real-finding path is {@code
     * PlaygroundMutationIT#mutationTargetFindsAKnownL3FindingWithNoDiffAtAllUnderNoVcs}).
     */
    @Test
    void mutationTargetLiftsTheNoVcsRestrictionAndAnUnresolvedTargetWarnsRatherThanFailing() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs", "--mutation-report",
            "--mutation-target", "root=com.example.NoSuchClass",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode, err.toString());
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());
        assertTrue(warningCodes(outFile).contains("MUTATION_TARGET_UNRESOLVED"), warningCodes(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertTrue(doc.at("/mutation/modules").isEmpty(), "no target resolved, so no module was ever collected: " + doc.at("/mutation"));
    }

    @Test
    void withoutMutationReportTheOutputHasNoMutationFieldAtAll() throws IOException {
        Path outFile = repoRoot.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertTrue(doc.at("/mutation").isMissingNode(), "mutation must be entirely absent, not null, when the flag is off");
    }

    @Test
    void anUnreadableClasspathFileWarnsAndTheRunStillCompletes() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "app=.",
            "--report", "app=" + FIXTURES.resolve("mixed-coverage.xml"),
            "--classpath", "app=does-not-exist.txt",
            "--out", outFile.toString());

        // D-17: an absent classpath degrades resolution, it never fails the run.
        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());
        assertTrue(warningCodes(outFile).contains("CLASSPATH_FILE_UNREADABLE"), warningCodes(outFile).toString());
    }

    @Test
    void aClasspathEntryThatIsNotAJarWarnsAndTheRunStillCompletes() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Files.writeString(repoRoot.resolve("deps.txt"), "not-a-real.jar\n");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "app=.",
            "--report", "app=" + FIXTURES.resolve("mixed-coverage.xml"),
            "--classpath", "app=deps.txt",
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());
        assertTrue(warningCodes(outFile).contains("CLASSPATH_ENTRY_UNUSABLE"), warningCodes(outFile).toString());
    }

    /**
     * The real end-to-end path with real jars: the same fixture-harness jars
     * {@code OracleRuleEngineFixturesTest} loads directly, reached here the way
     * a user reaches them - through a {@code --classpath} list file. Proves the
     * option is wired into both the parser's solver set and the scan, with no
     * warning and no change to the oracle verdict for an already-resolvable
     * import-anchored call (D-28's tier stays sufficient; D-17's "never
     * silently upgrades" holds).
     */
    @Test
    void aRealJarListIsLoadedWithNoWarningAndLeavesTheOracleVerdictUnchanged() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"),
            noOracleTestSource("CalcTest", "noAssertionHere"));

        Path harness = Path.of("target/fixture-harness").toAbsolutePath();
        Files.writeString(repoRoot.resolve("deps.txt"), String.join("\n",
            "# comment lines and blanks are skipped",
            "",
            harness.resolve("junit.jar").toString(),
            harness.resolve("assertj-core.jar").toString()) + "\n");
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "app=.",
            "--report", "app=" + FIXTURES.resolve("mixed-coverage.xml"),
            "--classpath", "app=deps.txt",
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());
        List<String> codes = warningCodes(outFile);
        assertFalse(codes.contains("CLASSPATH_FILE_UNREADABLE"), codes.toString());
        assertFalse(codes.contains("CLASSPATH_ENTRY_UNUSABLE"), codes.toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals(1, doc.at("/findings").size(), "the oracle-less test is still reported: " + doc.at("/findings"));
        assertEquals("NO_RECOGNIZED_ORACLE", doc.at("/findings/0/rule").asText());
    }

    /**
     * customOracles reaching the engine through the real CLI: the same
     * external-helper shape as {@code CustomOraclesTest}, but configured the
     * way a user configures it - a {@code coverdict.config.json} at the repo
     * root, picked up without being named.
     */
    @Test
    void aCustomOracleConfiguredInTheRepoRootConfigFileSuppressesTheFinding() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/MoreAsserts.java"), String.join("\n",
            "package com.example;",
            "public final class MoreAsserts {",
            "    public static void assertOk(String actual) {",
            "        if (actual == null) { throw new AssertionError(); }",
            "    }",
            "}",
            ""));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), String.join("\n",
            "package com.example;",
            "import static com.example.MoreAsserts.assertOk;",
            "import org.junit.jupiter.api.Test;",
            "class CalcTest {",
            "    @Test",
            "    void verifiesThroughAnExternalHelper() {",
            "        assertOk(\"value\");",
            "    }",
            "}",
            ""));

        String[] args = {"analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "app=.",
            "--report", "app=" + FIXTURES.resolve("mixed-coverage.xml"),
            "--out", outputDir.resolve("verdict.json").toString()};

        assertEquals(ExitCode.COMPLETE.value(), run(args));
        JsonNode before = new ObjectMapper().readTree(Files.readAllBytes(outputDir.resolve("verdict.json")));
        assertEquals(1, before.at("/findings").size(), "unconfigured, the external helper is not recognized");

        Files.writeString(repoRoot.resolve("coverdict.config.json"),
            "{\"customOracles\": [\"com.example.MoreAsserts#assertOk\"]}");

        assertEquals(ExitCode.COMPLETE.value(), run(args));
        JsonNode after = new ObjectMapper().readTree(Files.readAllBytes(outputDir.resolve("verdict.json")));
        assertEquals(0, after.at("/findings").size(), "configured, it is a recognized oracle: " + after.at("/findings"));
    }

    private List<String> warningCodes(Path jsonFile) throws IOException {
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(jsonFile));
        List<String> codes = new ArrayList<>();
        for (JsonNode w : doc.at("/warnings")) {
            codes.add(w.at("/code").asText());
        }
        return codes;
    }

    /**
     * Lines 10-14 match fixtures/jacoco/mixed-coverage.xml's own {@code <line>}
     * entries exactly, so editing one of them produces a diff hunk that lands
     * on a real report-known line - {@code editedLine12} replaces line 12
     * (mi=2, ci=1: jacoco-line covered, strict-line not).
     */
    private static String calcJavaBody(String editedLine12) {
        return String.join("\n",
            "package com.example;",
            "",
            "public class Calc {",
            "",
            "",
            "",
            "",
            "",
            "",
            "    int a = 1;",
            "    int b = 2;",
            "    " + editedLine12,
            "    int d = 4;",
            "    int e = 5;",
            "}",
            "");
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

    private static void initGitRepo(Path dir) throws IOException, InterruptedException {
        runGit(dir, "init", "-q", "-b", "main");
        runGit(dir, "config", "user.email", "test@example.com");
        runGit(dir, "config", "user.name", "Test");
        runGit(dir, "config", "core.autocrlf", "false");
        runGit(dir, "config", "commit.gpgsign", "false");
    }

    private static void commitAll(Path dir, String message) throws IOException, InterruptedException {
        runGit(dir, "add", "-A");
        runGit(dir, "commit", "-q", "-m", message);
    }

    // --- config-file modules (D-66): the same binding --module/--report
    // express, generated by 'coverdict doctor --write-config' ---

    @Test
    void configModulesAreUsedWhenTheCommandLineDeclaresNoModuleFlag() throws IOException {
        Files.copy(FIXTURES.resolve("mixed-coverage.xml"), repoRoot.resolve("jacoco.xml"));
        Files.writeString(repoRoot.resolve("coverdict.config.json"), """
            {"modules": [{"id": "root", "root": ".", "report": "jacoco.xml"}]}
            """);
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs", "--repo", repoRoot.toString(), "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("root", doc.at("/inputs/modules/0/id").asText());
        assertEquals(80.0, doc.at("/coverage/overall/jacoco-line/percent").asDouble());
    }

    /**
     * The all-or-nothing rule (same as {@code coverageExclusions}): one
     * {@code --module} on the command line makes the config's whole {@code
     * modules} array invisible, not partially merged with it. Proven here
     * by a CLI module with no {@code --report} bound - if config's
     * perfectly valid module leaked through, this run would be complete,
     * not incomplete.
     */
    @Test
    void aSingleCommandLineModuleFlagIgnoresConfigModulesEntirely() throws IOException {
        Files.copy(FIXTURES.resolve("mixed-coverage.xml"), repoRoot.resolve("jacoco.xml"));
        Files.writeString(repoRoot.resolve("coverdict.config.json"), """
            {"modules": [{"id": "fromconfig", "root": ".", "report": "jacoco.xml"}]}
            """);
        Path outFile = outputDir.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs", "--repo", repoRoot.toString(),
            "--module", "clioverride=.", "--out", outFile.toString());

        // Neither module ends up with bound-report evidence: 'clioverride'
        // because the command line never bound a --report to it,
        // 'fromconfig' because a --module on the command line must make it
        // as if the config's modules array was never declared at all.
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertTrue(doc.at("/inputs/modules").isMissingNode() || doc.at("/inputs/modules").isEmpty(),
            "config's module must not have leaked through: " + doc.at("/inputs/modules"));
        String warnings = doc.at("/warnings").toString();
        assertTrue(warnings.contains("MODULE_WITHOUT_REPORT") && warnings.contains("clioverride"), warnings);
        assertFalse(warnings.contains("fromconfig"), warnings);
    }

    /** Test-only scaffolding: builds fixture repository state. Never used to exercise the code under test. */
    private static String runGit(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(command).directory(dir.toFile()).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed (exit " + exit + "): " + err);
        }
        return out;
    }
}
