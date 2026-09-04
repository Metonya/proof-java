package dev.proofjava.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

import picocli.CommandLine;

import dev.proofjava.analysis.report.ToolVersion;

/**
 * M1c criterion 1's second layer, alongside schema/examples/'s hand-written
 * contract goldens: these two checked-in files (fixtures/verdicts/no-vcs.json,
 * base-ref.json) are byte-for-byte tool OUTPUT, not hand-authored, produced
 * from a synthetic in-repo fixture repo
 * this test rebuilds deterministically on every run (fixed git author/
 * committer identity and date, so commit SHAs are reproducible - verified
 * separately, not an assumption). The only non-analytic field is {@code
 * tool.version}: a checked-in {@code ${tool.version}} placeholder is
 * substituted with the real build's version before comparison, so every
 * other byte in the golden is the real analyzer's real output.
 *
 * <p>To regenerate after a deliberate output-format change: run this test
 * once with {@code -Dcoverdict.regenerateGoldens=true} (see {@link
 * #compareOrRegenerate}), inspect the diff, then re-add the {@code
 * ${tool.version}} placeholder by hand before committing (the regenerated
 * file otherwise contains this build's literal version string).
 */
class VerdictGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("../fixtures/verdicts");
    private static final Path JACOCO_FIXTURE = Path.of("../fixtures/jacoco/mixed-coverage.xml").toAbsolutePath();
    private static final Path SCHEMA_FILE = Path.of("../schema/coverdict-verdict.schema.json");
    private static final String VERSION_PLACEHOLDER = "${tool.version}";

    @TempDir
    Path repoRoot;

    @TempDir
    Path outputDir;

    @Test
    void noVcsCompleteRunMatchesTheCheckedInGoldenByteForByte() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), calcJavaBody());
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), noOracleTestSource());
        copyJacocoFixtureIntoRepo(repoRoot);

        Path out = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", "coverage.xml",
            "--out", out.toString());
        assertEquals(0, exitCode);

        compareOrRegenerate("no-vcs.json", out);
    }

    @Test
    void baseRefRunMatchesTheCheckedInGoldenByteForByte() throws IOException, InterruptedException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Path calc = repoRoot.resolve("src/main/java/com/example/Calc.java");
        Files.writeString(calc, calcJavaBody());
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), noOracleTestSource());
        copyJacocoFixtureIntoRepo(repoRoot);
        deterministicInitGitRepo(repoRoot);
        deterministicCommitAll(repoRoot, "base");
        Files.writeString(calc, calcJavaBody().replace("int c = 3;", "int c = 30;"));
        deterministicCommitAll(repoRoot, "edit line 12");

        Path out = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--base", "HEAD~1",
            "--repo", repoRoot.toString(),
            "--report", "coverage.xml",
            "--out", out.toString());
        assertEquals(0, exitCode);

        compareOrRegenerate("base-ref.json", out);
    }

    @Test
    void noVcsFileCoverageRunMatchesTheCheckedInGoldenByteForByte() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), calcJavaBody());
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), noOracleTestSource());
        copyJacocoFixtureIntoRepo(repoRoot);

        Path out = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs", "--file-coverage",
            "--repo", repoRoot.toString(),
            "--report", "coverage.xml",
            "--out", out.toString());
        assertEquals(0, exitCode);

        compareOrRegenerate("file-coverage.json", out);
    }

    /**
     * A second, independent run to prove the determinism claim itself, not
     * just that the checked-in golden happens to match one run.
     */
    @Test
    void twoIndependentRunsOnFreshlyRebuiltReposAreByteIdentical() throws IOException {
        Path outA = outputDir.resolve("a.json");
        Path outB = outputDir.resolve("b.json");
        runNoVcsInto(outA);
        runNoVcsInto(outB);
        assertArrayEquals(Files.readAllBytes(outA), Files.readAllBytes(outB));
    }

    private void runNoVcsInto(Path out) throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), calcJavaBody());
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), noOracleTestSource());
        copyJacocoFixtureIntoRepo(repoRoot);
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", "coverage.xml",
            "--out", out.toString());
        assertEquals(0, exitCode);
    }

    // The report path is written verbatim into inputs.modules[].reports[].path
    // (unverified freshness) - an absolute --report argument would bake this
    // machine's own filesystem path into the checked-in golden, breaking the
    // byte comparison on every other machine and every future checkout. Copy
    // the fixture INTO the synthetic repo instead, so --report is relative.
    private static void copyJacocoFixtureIntoRepo(Path repoRoot) throws IOException {
        Files.copy(JACOCO_FIXTURE, repoRoot.resolve("coverage.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void compareOrRegenerate(String goldenFileName, Path producedOut) throws IOException {
        byte[] produced = Files.readAllBytes(producedOut);
        String producedText = new String(produced, StandardCharsets.UTF_8);
        String toolVersion = ToolVersion.read().version();

        assertTrue(validate(producedOut).isEmpty(), "produced verdict must itself validate: " + validate(producedOut));

        Path goldenPath = GOLDEN_DIR.resolve(goldenFileName);
        if (Boolean.getBoolean("coverdict.regenerateGoldens")) {
            Files.writeString(goldenPath, producedText, StandardCharsets.UTF_8);
            return;
        }

        String goldenText = Files.readString(goldenPath, StandardCharsets.UTF_8);
        String goldenWithRealVersion = goldenText.replace(VERSION_PLACEHOLDER, toolVersion);
        assertEquals(goldenWithRealVersion, producedText,
            "golden mismatch for " + goldenFileName + " - if this is a deliberate output-format change, "
                + "re-run with -Dcoverdict.regenerateGoldens=true and re-add the " + VERSION_PLACEHOLDER + " placeholder by hand");
    }

    private Set<ValidationMessage> validate(Path jsonFile) throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012);
        JsonSchema schema;
        try (var in = Files.newInputStream(SCHEMA_FILE)) {
            schema = factory.getSchema(in);
        }
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(jsonFile));
        return schema.validate(node);
    }

    private int run(String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(new StringWriter()));
        cmd.setErr(new PrintWriter(new StringWriter()));
        return cmd.execute(args);
    }

    private static String calcJavaBody() {
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
            "    int c = 3;",
            "    int d = 4;",
            "    int e = 5;",
            "}",
            "");
    }

    private static String noOracleTestSource() {
        return String.join("\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class CalcTest {",
            "    @Test",
            "    void printsResultOnly() {",
            "        System.out.println(\"no assertion here\");",
            "    }",
            "}",
            "");
    }

    private static void deterministicInitGitRepo(Path dir) throws IOException, InterruptedException {
        runGit(dir, Map.of(), "init", "-q", "-b", "main");
        runGit(dir, Map.of(), "config", "user.email", "test@example.com");
        runGit(dir, Map.of(), "config", "user.name", "Test");
        runGit(dir, Map.of(), "config", "core.autocrlf", "false");
        runGit(dir, Map.of(), "config", "commit.gpgsign", "false");
    }

    // Fixed author/committer identity and timestamp (D-29): every element a
    // commit SHA is computed from is pinned, so the SHA reproduces exactly
    // across machines and runs, verified by hand before this test was written.
    private static void deterministicCommitAll(Path dir, String message) throws IOException, InterruptedException {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("GIT_AUTHOR_NAME", "Test");
        env.put("GIT_AUTHOR_EMAIL", "test@example.com");
        env.put("GIT_AUTHOR_DATE", "2026-01-01T00:00:00");
        env.put("GIT_COMMITTER_NAME", "Test");
        env.put("GIT_COMMITTER_EMAIL", "test@example.com");
        env.put("GIT_COMMITTER_DATE", "2026-01-01T00:00:00");
        runGit(dir, Map.of(), "add", "-A");
        runGit(dir, env, "commit", "-q", "-m", message);
    }

    private static String runGit(Path dir, Map<String, String> extraEnv, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile());
        pb.environment().putAll(extraEnv);
        Process p = pb.start();
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        p.getInputStream().transferTo(outBytes);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed (exit " + exit + "): " + err);
        }
        return outBytes.toString(StandardCharsets.UTF_8);
    }
}
