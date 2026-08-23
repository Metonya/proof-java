package dev.coverdict.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.binding.BindingResult;
import dev.coverdict.analysis.binding.ModuleBinder;
import dev.coverdict.analysis.jacoco.JacocoReport;
import dev.coverdict.analysis.jacoco.JacocoXmlParser;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.ResolvedSourceFile;

/** End-to-end through parse -> bind -> exclude -> compute, using the same fixtures the earlier stages test. */
class MetricsEngineTest {

    private static final Path FIXTURES = Path.of("../fixtures/jacoco");
    private final JacocoXmlParser parser = new JacocoXmlParser();

    @TempDir
    Path repoRoot;

    private List<ResolvedSourceFile> bind(String fixtureFile, String... sourceRoots) {
        ModuleDefinition module = new ModuleDefinition("demo", ".", List.of(sourceRoots), List.of());
        JacocoReport report = parser.parse(FIXTURES.resolve(fixtureFile));
        BindingResult result = new ModuleBinder(repoRoot).bind(List.of(module), Map.of("demo", List.of(report)));
        return result.resolvedFiles();
    }

    @Test
    void computesAllThreeModesFromTheHandCalculatedFixture() {
        // See fixtures/jacoco/mixed-coverage.xml's header comment for the
        // by-hand derivation: 80.0% / 60.0% / 55.6%, all different.
        MetricSet metrics = MetricsEngine.computeOverall(bind("mixed-coverage.xml", "src"));

        assertEquals(new BigDecimal("80.0"), metrics.jacocoLine().percent());
        assertEquals(4, metrics.jacocoLine().numerator());
        assertEquals(5, metrics.jacocoLine().denominator());

        assertEquals(new BigDecimal("60.0"), metrics.strictLine().percent());
        assertEquals(3, metrics.strictLine().numerator());
        assertEquals(5, metrics.strictLine().denominator());

        assertEquals(new BigDecimal("55.6"), metrics.sonarCompatible().percent());
        assertEquals(5, metrics.sonarCompatible().numerator());
        assertEquals(9, metrics.sonarCompatible().denominator());
    }

    @Test
    void jacocoLineMatchesJacocosOwnReportLevelLineCounterExactly() {
        // D-04 parity requirement, checked against the fixture's own
        // <counter type="LINE"> at report level rather than a re-derived
        // number, so a bug shared between the fixture and the engine can't
        // hide the mismatch.
        JacocoReport report = parser.parse(FIXTURES.resolve("mixed-coverage.xml"));
        MetricSet metrics = MetricsEngine.computeOverall(bind("mixed-coverage.xml", "src"));

        assertEquals(report.totalLineCovered(), metrics.jacocoLine().numerator());
        assertEquals(report.totalLineCovered() + report.totalLineMissed(), metrics.jacocoLine().denominator());
    }

    @Test
    void zeroExecutableLinesProducesNullPercentNotZeroOrHundred() {
        MetricSet metrics = MetricsEngine.computeOverall(bind("empty-report.xml", "src"));
        assertNull(metrics.jacocoLine().percent());
        assertNull(metrics.strictLine().percent());
        assertNull(metrics.sonarCompatible().percent());
        assertEquals(0, metrics.jacocoLine().denominator());
    }

    @Test
    void exclusionsAreAppliedBeforeMetricsAreComputedNotAfter() {
        List<ResolvedSourceFile> files = bind("mixed-coverage.xml", "src");
        List<ResolvedSourceFile> excluded = ExclusionFilter.apply(files, List.of("**/Calc.java"));

        MetricSet metrics = MetricsEngine.computeOverall(excluded);

        assertEquals(0, metrics.jacocoLine().denominator(), "the only source file was excluded");
        assertNull(metrics.jacocoLine().percent());
    }
}
