package dev.coverdict.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;

import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * Runs the real engine over every fixture under {@code fixtures/rules/} and
 * checks its output against that fixture's own {@code // expect:} header
 * comments (docs/rules/README.md's fixture convention) - the M0 rule specs
 * become executable here rather than staying aspirational prose.
 *
 * <p>The fixture harness jars (JUnit 4/5, AssertJ, Mockito, Hamcrest) are
 * loaded from {@code target/fixture-harness/} (K4, pom.xml's
 * {@code copy-fixture-harness} execution) via a standalone
 * {@link JarTypeSolver} per jar - never added to this module's own
 * classpath, so a resolution that works here would work identically for a
 * real user with a {@code --classpath} pointing at the same jars.
 */
class OracleRuleEngineFixturesTest {

    private static final Path FIXTURES_ROOT = Path.of("../fixtures/rules");
    private static final Path HARNESS_DIR = Path.of("target/fixture-harness");
    private static final Pattern EXPECT_LINE =
        Pattern.compile("^//\\s*expect:\\s*(finding|none)\\s+method=(\\S+?)(?:\\s+confidence=(\\S+))?\\s*$");

    @ParameterizedTest
    @ValueSource(strings = {"NO_RECOGNIZED_ORACLE", "TAUTOLOGICAL_ORACLE", "CATCH_ORACLE_WITHOUT_FAIL", "NULL_CHECK_ONLY"})
    void fixtureExpectationsMatchTheEngine(String ruleId) throws IOException {
        Path dir = FIXTURES_ROOT.resolve(ruleId);
        Map<String, Confidence> expectFinding = new LinkedHashMap<>();
        Map<String, Boolean> expectNone = new LinkedHashMap<>();
        for (String fixtureFile : List.of("Positive.java", "Negative.java", "Unresolved.java")) {
            parseExpectations(dir.resolve(fixtureFile), expectFinding, expectNone);
        }

        Map<String, Finding> actual = scanFixtureDirectory(ruleId, dir);

        for (Map.Entry<String, Confidence> e : expectFinding.entrySet()) {
            Finding finding = actual.get(e.getKey());
            if (finding == null) {
                fail("expected a " + ruleId + " finding for method=" + e.getKey() + " but the engine reported none");
            }
            assertEquals(e.getValue(), finding.confidence(), "confidence for method=" + e.getKey());
        }
        for (String method : expectNone.keySet()) {
            assertNull(actual.get(method), "expected no " + ruleId + " finding for method=" + method
                + " but got: " + actual.get(method));
        }
    }

    private static Map<String, Finding> scanFixtureDirectory(String ruleId, Path dir) {
        ModuleDefinition module = new ModuleDefinition("fixtures", ".", List.of(), List.of("."));
        OracleScanResult scan = OracleRuleEngine.scan(dir, List.of(module), 17, "UTF-8", null,
            OracleScanOptions.defaults().withTypeSolvers(fixtureHarnessSolvers()));

        Map<String, Finding> byMethod = new LinkedHashMap<>();
        for (Finding f : scan.findings()) {
            if (ruleId.equals(f.rule())) {
                byMethod.put(methodName(f.testMethod()), f);
            }
        }
        return byMethod;
    }

    private static List<TypeSolver> fixtureHarnessSolvers() {
        try {
            return List.of(
                new JarTypeSolver(HARNESS_DIR.resolve("junit.jar")),
                new JarTypeSolver(HARNESS_DIR.resolve("assertj-core.jar")),
                new JarTypeSolver(HARNESS_DIR.resolve("mockito-core.jar")),
                new JarTypeSolver(HARNESS_DIR.resolve("hamcrest.jar")),
                new JarTypeSolver(HARNESS_DIR.resolve("truth.jar")));
        } catch (IOException e) {
            throw new IllegalStateException("fixture-harness jars missing - run 'mvn generate-test-resources' first", e);
        }
    }

    /** {@code Outer#name(params)} -> {@code name}. */
    private static String methodName(String signature) {
        int hash = signature.indexOf('#');
        int paren = signature.indexOf('(');
        return signature.substring(hash + 1, paren);
    }

    private static void parseExpectations(Path file, Map<String, Confidence> expectFinding, Map<String, Boolean> expectNone) throws IOException {
        for (String line : Files.readAllLines(file)) {
            Matcher m = EXPECT_LINE.matcher(line.strip());
            if (!m.matches()) {
                continue;
            }
            String method = m.group(2);
            if ("finding".equals(m.group(1))) {
                expectFinding.put(method, Confidence.valueOf(m.group(3)));
            } else {
                expectNone.put(method, Boolean.TRUE);
            }
        }
    }
}
