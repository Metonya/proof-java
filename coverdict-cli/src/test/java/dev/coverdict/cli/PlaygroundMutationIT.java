package dev.coverdict.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;

/**
 * Runs coverdict's real {@code analyze --base <ref> --mutation-report}
 * against a real PIT mutation run of the checked-in playground fixture -
 * the L3 counterpart to {@link PlaygroundFunctionalTest}'s L0-only run,
 * which explicitly defers this to the {@code mutation-it} profile.
 *
 * <p>Unlike {@link
 * dev.coverdict.analysis.mutation.MutationRunnerIT}, which mutates
 * coverdict's own already-built classes (free classpath via {@code
 * java.class.path}), the playground fixture is a separate, not-yet-built
 * Maven project: its classpath has to be assembled the same way
 * coverdict-playground's own {@code run.ps1} does it - a real {@code mvn}
 * subprocess for {@code test-compile} and {@code dependency:build-classpath}
 * - since nothing puts the fixture's jars on this JVM's classpath for free.
 *
 * <p>{@code --mutation-report} is rejected under {@code --no-vcs} (no diff,
 * no changed-class targets), so this test commits the fixture in two steps
 * (a skeleton, then the real scenario sources) and uses {@code --base
 * <first-commit>} - the same shape coverdict-playground's own {@code
 * run.ps1} uses against the real repo's first commit.
 */
class PlaygroundMutationIT {

    private static final Path FIXTURE_ROOT = fixtureRoot();

    @TempDir
    Path repoRoot;

    @TempDir
    Path workDir;

    @Test
    void mutationReportFindsThePlaygroundsFourKnownL3Findings() throws IOException, InterruptedException {
        Files.copy(FIXTURE_ROOT.resolve("pom.xml"), repoRoot.resolve("pom.xml"));
        deterministicInitGitRepo(repoRoot);
        deterministicCommitAll(repoRoot, "skeleton");
        String firstCommit = runGit(repoRoot, Map.of(), "rev-parse", "HEAD").trim();

        copyDirectory(FIXTURE_ROOT.resolve("src"), repoRoot.resolve("src"));
        Files.copy(FIXTURE_ROOT.resolve("jacoco.xml"), repoRoot.resolve("jacoco.xml"));
        deterministicCommitAll(repoRoot, "add playground scenarios");

        runMaven(repoRoot, "test-compile");
        Path mutationClasspath = buildMutationClasspathFile(repoRoot);

        Path out = workDir.resolve("verdict.json");
        Path diagnosticsDir = workDir.resolve("diagnostics");
        StringWriter err = new StringWriter();
        int exitCode = run(err, "analyze",
            "--repo", repoRoot.toString(),
            "--base", firstCommit,
            "--report", "jacoco.xml",
            "--mutation-report",
            "--mutation-classpath", "root=" + mutationClasspath,
            "--diagnostics-dir", diagnosticsDir.toString(),
            "--out", out.toString());
        assertEquals(0, exitCode, "expected a complete analyze run");

        assertMutationRunWasObservable(err.toString(), diagnosticsDir);

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(out));
        List<String> findings = mutationFindingSummaries(doc);

        // Ground truth from a real `./run.ps1` run against coverdict-playground
        // (private repo) - see that repo's README scenario table and
        // checked-in coverdict-verdict.json.
        String pkg = "dev.coverdict.playground.";
        assertEquals(4, findings.size(), "unexpected L3 finding count: " + findings);
        assertTrue(findings.contains("PSEUDO_TESTED_METHOD HIGH " + pkg + "Calculator#subtract(II)I"));
        assertTrue(findings.contains("PSEUDO_TESTED_METHOD HIGH " + pkg + "Calculator#isPositive(I)Z"));
        assertTrue(findings.contains("PSEUDO_TESTED_METHOD HIGH " + pkg + "Calculator#square(I)I"));
        assertTrue(findings.contains("SUBSUMED_TEST MEDIUM " + pkg
            + "CalculatorSubsumedTest.[engine:junit-jupiter]/[class:" + pkg + "CalculatorSubsumedTest]/[method:divideNarrow()]"));
    }

    private static List<String> mutationFindingSummaries(JsonNode doc) {
        return java.util.stream.StreamSupport.stream(doc.get("findings").spliterator(), false)
            .filter(f -> f.get("rule").asText().equals("PSEUDO_TESTED_METHOD") || f.get("rule").asText().equals("SUBSUMED_TEST"))
            .map(f -> {
                String rule = f.get("rule").asText();
                String confidence = f.get("confidence").asText();
                String anchor = rule.equals("PSEUDO_TESTED_METHOD")
                    ? f.get("productionMethod").asText()
                    : f.get("testMethod").asText();
                return rule + " " + confidence + " " + anchor;
            })
            .toList();
    }

    /**
     * D-64's two observability channels, asserted against a real PIT
     * subprocess rather than a stub - the WTA dogfood's central lesson was
     * that a mutation run which reports nothing while it works cannot be
     * diagnosed when it goes wrong.
     */
    private static void assertMutationRunWasObservable(String err, Path diagnosticsDir) throws IOException {
        assertTrue(err.contains("target class(es)"), "expected a preflight target count on stderr, got: " + err);
        assertTrue(err.contains("mutation: module 'root' - done"),
            "expected a per-module completion line on stderr, got: " + err);

        Path log = diagnosticsDir.resolve("root-mutation.log");
        assertTrue(Files.exists(log), "expected --diagnostics-dir to write " + log);
        String logged = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(logged.contains("##coverdict-progress "),
            "expected per-class progress markers in the log, got: " + logged);
        assertTrue(logged.contains("PIT >>"),
            "expected verbose engine output in the log (the whole point of --diagnostics-dir), got: " + logged);
    }

    private int run(StringWriter err, String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(new StringWriter()));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute(args);
    }

    private static Path buildMutationClasspathFile(Path repoRoot) throws IOException, InterruptedException {
        Path rawClasspathFile = repoRoot.resolve("cp.txt");
        runMaven(repoRoot, "dependency:build-classpath", "-Dmdep.outputFile=" + rawClasspathFile);
        String raw = Files.readString(rawClasspathFile, StandardCharsets.UTF_8).trim();
        Files.delete(rawClasspathFile);

        List<String> entries = new ArrayList<>(Arrays.asList(raw.split(File.pathSeparator)));
        entries.add(repoRoot.resolve("target/classes").toAbsolutePath().toString());
        entries.add(repoRoot.resolve("target/test-classes").toAbsolutePath().toString());

        Path mutationClasspathFile = repoRoot.resolve("mutation-classpath.txt");
        Files.write(mutationClasspathFile, entries, StandardCharsets.UTF_8);
        return mutationClasspathFile;
    }

    private static void runMaven(Path repoRoot, String... goalsAndArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(isWindows() ? "mvn.cmd" : "mvn");
        command.add("-q");
        command.add("-f");
        command.add(repoRoot.resolve("pom.xml").toString());
        command.addAll(Arrays.asList(goalsAndArgs));
        Process p = new ProcessBuilder(command).directory(repoRoot.toFile()).start();
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        p.getInputStream().transferTo(outBytes);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("mvn " + String.join(" ", goalsAndArgs) + " failed (exit " + exit + "): "
                + outBytes.toString(StandardCharsets.UTF_8) + err);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void deterministicInitGitRepo(Path dir) throws IOException, InterruptedException {
        runGit(dir, Map.of(), "init", "-q", "-b", "main");
        runGit(dir, Map.of(), "config", "user.email", "test@example.com");
        runGit(dir, Map.of(), "config", "user.name", "Test");
        runGit(dir, Map.of(), "config", "core.autocrlf", "false");
        runGit(dir, Map.of(), "config", "commit.gpgsign", "false");
    }

    private static void deterministicCommitAll(Path dir, String message) throws IOException, InterruptedException {
        Map<String, String> env = Map.of(
            "GIT_AUTHOR_NAME", "Test",
            "GIT_AUTHOR_EMAIL", "test@example.com",
            "GIT_AUTHOR_DATE", "2026-01-01T00:00:00",
            "GIT_COMMITTER_NAME", "Test",
            "GIT_COMMITTER_EMAIL", "test@example.com",
            "GIT_COMMITTER_DATE", "2026-01-01T00:00:00");
        runGit(dir, Map.of(), "add", "-A");
        runGit(dir, env, "commit", "-q", "-m", message);
    }

    private static String runGit(Path dir, Map<String, String> extraEnv, String... args)
            throws IOException, InterruptedException {
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

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest);
                }
            }
        }
    }

    private static Path fixtureRoot() {
        try {
            URL url = PlaygroundMutationIT.class.getResource("/functional/playground");
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
