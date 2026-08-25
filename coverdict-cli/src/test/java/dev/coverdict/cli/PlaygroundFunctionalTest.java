package dev.coverdict.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;

/**
 * Runs coverdict's real {@code analyze --no-vcs} against a checked-in copy of
 * coverdict-playground (a separate, private repo of deliberately constructed
 * test-quality scenarios, one per L0 rule - see that repo's README for the
 * scenario map). Unlike the synthetic two-line fixtures in {@link
 * dev.coverdict.cli.VerdictGoldenTest}, this exercises every L0 rule at once
 * against a realistic multi-file source tree with a real checked-in JaCoCo
 * report, and pins the exact finding set so a future regression in any rule
 * (not just the one under active development) fails a test immediately.
 *
 * <p>L3 evidence ({@code --mutation-report}) needs a real PIT subprocess run
 * against compiled classes and is intentionally not exercised here - it
 * belongs behind the {@code -Pmutation-it} profile alongside {@link
 * dev.coverdict.analysis.mutation.MutationRunnerIT}, the same reason that
 * class stays out of the default {@code mvn verify} run.
 */
class PlaygroundFunctionalTest {

    private static final Path FIXTURE_ROOT = fixtureRoot();

    @TempDir
    Path repoRoot;

    @TempDir
    Path outputDir;

    @BeforeEach
    void copyFixtureIntoTempRepo() throws IOException {
        copyDirectory(FIXTURE_ROOT.resolve("src"), repoRoot.resolve("src"));
        Files.copy(FIXTURE_ROOT.resolve("jacoco.xml"), repoRoot.resolve("jacoco.xml"));
    }

    @Test
    void everyL0RuleFiresExactlyOnItsKnownScenarioAndNowhereElse() throws IOException {
        Path out = outputDir.resolve("verdict.json");
        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", "jacoco.xml",
            "--out", out.toString());
        assertEquals(0, exitCode);

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(out));
        List<String> findings = findingSummaries(doc);

        // Ground truth verified by hand against coverdict-playground (private
        // repo): `analyze --base <first-commit> --report jacoco.xml
        // --mutation-report` produces these same six L0 findings plus four L3
        // findings this test doesn't exercise. CalculatorGoodTest and
        // CalculatorSubsumedTest contribute none, confirming no false positives.
        String pkg = "dev.coverdict.playground.";
        assertEquals(6, findings.size(), "unexpected finding count: " + findings);
        assertTrue(findings.contains("CATCH_ORACLE_WITHOUT_FAIL HIGH " + pkg + "CalculatorCatchWithoutFailTest#divideByZeroSwallowed()"));
        assertTrue(findings.contains("NO_RECOGNIZED_ORACLE HIGH " + pkg + "CalculatorNoOracleTest#subtractHasNoAssertion()"));
        assertTrue(findings.contains("NO_RECOGNIZED_ORACLE HIGH " + pkg + "CalculatorPseudoTestedTest#squareHasNoAssertion()"));
        assertTrue(findings.contains("NULL_CHECK_ONLY HIGH " + pkg + "CalculatorNullCheckOnlyTest#describeOnlyChecksNonNull()"));
        assertTrue(findings.contains("TAUTOLOGICAL_ORACLE HIGH " + pkg + "CalculatorTautologicalOracleTest#multiplyConstantVsConstant()"));
        assertTrue(findings.contains("TAUTOLOGICAL_ORACLE HIGH " + pkg + "CalculatorTautologicalOracleTest#multiplyLiteralBoolean()"));
    }

    private static List<String> findingSummaries(JsonNode doc) {
        return java.util.stream.StreamSupport.stream(doc.get("findings").spliterator(), false)
            .map(f -> f.get("rule").asText() + " " + f.get("confidence").asText() + " " + f.get("testMethod").asText())
            .collect(Collectors.toList());
    }

    private int run(String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(new StringWriter()));
        cmd.setErr(new PrintWriter(new StringWriter()));
        return cmd.execute(args);
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
            URL url = PlaygroundFunctionalTest.class.getResource("/functional/playground");
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
