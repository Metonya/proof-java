package dev.proofjava.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.proofjava.analysis.jacoco.LineCoverage;
import dev.proofjava.analysis.metrics.MetricsEngine;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.Confidence;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.LineRange;
import dev.proofjava.analysis.model.Severity;
import dev.proofjava.analysis.mutation.Mutant;
import dev.proofjava.analysis.mutation.MutatedMethod;
import dev.proofjava.analysis.mutation.MutationModuleEvidence;
import dev.proofjava.analysis.pertest.PerTestEntry;
import dev.proofjava.analysis.pertest.PerTestLine;
import dev.proofjava.analysis.pertest.PerTestModuleEvidence;
import dev.proofjava.analysis.vcs.VcsIdentity;

/**
 * D-78: {@link VerdictJsonReader} must round-trip everything {@link
 * VerdictJsonWriter} writes - it is the counterpart a synthetic,
 * VS-Code-composed verdict JSON is read back through before {@link
 * HtmlRenderer} ever sees it, so a silent field drop here would silently
 * blank out real evidence in the exported report.
 */
class VerdictJsonReaderTest {

    private ModuleInput module() {
        return new ModuleInput("root", ".", List.of("src/main/java"), List.of("src/test/java"),
            List.of(new ReportInput("target/site/jacoco/jacoco.xml", "unverified")));
    }

    private static VerdictDocument roundTrip(VerdictDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, doc);
        return VerdictJsonReader.read(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    void roundTripsANoVcsCompleteDocumentWithNoOptionalBlocks() throws IOException {
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of("**/generated/**"), List.of(module()), "no-vcs", "all", null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"),
            List.of(), List.of(), List.of());

        VerdictDocument read = roundTrip(doc);

        assertEquals(doc.schemaVersion(), read.schemaVersion());
        assertEquals(doc.toolVersion(), read.toolVersion());
        assertEquals(doc.complete(), read.complete());
        assertEquals(doc.diffMode(), read.diffMode());
        assertEquals(doc.findingsScope(), read.findingsScope());
        assertEquals(doc.languageLevel(), read.languageLevel());
        assertEquals(doc.encoding(), read.encoding());
        assertEquals(doc.exclusions(), read.exclusions());
        assertEquals(doc.modules(), read.modules());
        assertEquals(doc.newCode().unavailableStatus(), read.newCode().unavailableStatus());
        assertNull(read.identity());
        assertNull(read.perTest());
        assertNull(read.mutation());
        assertNull(read.fileCoverage());
    }

    @Test
    void roundTripsBaseRefModeIdentityAndAnIncompleteReason() throws IOException {
        VcsIdentity identity = new VcsIdentity("abc123abc123abc123abc123abc123abc123ab1", "main",
            "abc123abc123abc123abc123abc123abc123ab1", "abc123abc123abc123abc123abc123abc123ab1", true);
        AnalysisReason reason = new AnalysisReason("SOME_CODE", "something missing", "src/Foo.java", "root", 3);

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", false, List.of(reason),
            17, "UTF-8", List.of(), List.of(module()), "base-ref", "all", identity,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_incomplete"),
            List.of(), List.of(), List.of());

        VerdictDocument read = roundTrip(doc);

        assertEquals(1, read.incompleteReasons().size());
        AnalysisReason readReason = read.incompleteReasons().get(0);
        assertEquals(reason.code(), readReason.code());
        assertEquals(reason.message(), readReason.message());
        assertEquals(reason.path(), readReason.path());
        assertEquals(reason.module(), readReason.module());
        assertEquals(reason.count(), readReason.count());
        assertEquals(identity, read.identity());
    }

    @Test
    void roundTripsChangedFilesAndFindings() throws IOException {
        ChangedFile mapped = new ChangedFile("src/main/java/Foo.java", "root", Classification.MAPPED, 10, 7,
            List.of(new LineRange(3, 5), new LineRange(9, 9)));
        ChangedFile unmapped = new ChangedFile("src/main/java/Bar.java", null, Classification.UNKNOWN, null, null, List.of());
        Finding finding = new Finding("SUBSUMED_TEST", Severity.INFO, Confidence.MEDIUM, "root", "src/test/FooTest.java",
            10, 12, "com.example.FooTest#a()", "message", "suggestion", "0123456789abcdef", null,
            "com.example.FooTest#b()", "src/test/FooOtherTest.java");

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "uncommitted", "all", null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.available(MetricsEngine.compute(List.of())),
            List.of(mapped, unmapped), List.of(finding), List.of());

        VerdictDocument read = roundTrip(doc);

        assertEquals(2, read.changedFiles().size());
        ChangedFile readMapped = read.changedFiles().stream().filter(f -> f.path().equals(mapped.path())).findFirst().orElseThrow();
        assertEquals(mapped.classification(), readMapped.classification());
        assertEquals(mapped.newLines(), readMapped.newLines());
        assertEquals(mapped.coveredNewLines(), readMapped.coveredNewLines());
        assertEquals(mapped.uncoveredNewRanges(), readMapped.uncoveredNewRanges());
        ChangedFile readUnmapped = read.changedFiles().stream().filter(f -> f.path().equals(unmapped.path())).findFirst().orElseThrow();
        assertNull(readUnmapped.module());
        assertNull(readUnmapped.newLines());

        assertEquals(1, read.findings().size());
        Finding readFinding = read.findings().get(0);
        assertEquals(finding.rule(), readFinding.rule());
        assertEquals(finding.severity(), readFinding.severity());
        assertEquals(finding.confidence(), readFinding.confidence());
        assertEquals(finding.relatedTestMethod(), readFinding.relatedTestMethod());
        assertEquals(finding.relatedPath(), readFinding.relatedPath());
        assertNotNull(read.newCode().metrics());
    }

    @Test
    void roundTripsPerTestMutationAndFileCoverageBlocks() throws IOException {
        PerTestModuleEvidence perTestModule = new PerTestModuleEvidence("root",
            List.of(new PerTestEntry("com.example.Calculator", "add", List.of(new PerTestLine(10, List.of("com.example.CalculatorTest#add()"))))),
            List.of(new PerTestEntry("com.example.Calculator", "<clinit>", List.of(new PerTestLine(1, List.of("com.example.CalculatorTest#add()"))))));
        Mutant killed = new Mutant("RETURNS", 10, "KILLED", List.of("com.example.CalculatorTest#add()"));
        MutatedMethod method = new MutatedMethod("com.example.Calculator", "add", "(II)I", 10, 11, List.of(killed));
        MutationModuleEvidence mutationModule = new MutationModuleEvidence("root", List.of(method), List.of());
        FileCoverageEntry fileEntry = new FileCoverageEntry("root", "src/main/java/com/example/Calculator.java",
            MetricsEngine.compute(List.of()), List.of(new LineCoverage(10, 1, 3, 0, 2)));
        FileCoverageBlock fileCoverage = new FileCoverageBlock(List.of(fileEntry), List.of("src/main/java/Excluded.java"));

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "uncommitted", "all", null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"),
            List.of(), List.of(), List.of(), List.of(perTestModule), List.of(mutationModule), fileCoverage);

        VerdictDocument read = roundTrip(doc);

        assertEquals(1, read.perTest().size());
        assertEquals(perTestModule.entries().get(0).className(), read.perTest().get(0).entries().get(0).className());
        assertEquals(perTestModule.ambient().get(0).methodName(), read.perTest().get(0).ambient().get(0).methodName());
        assertEquals(1, read.mutation().size());
        assertEquals(killed.status(), read.mutation().get(0).methods().get(0).mutants().get(0).status());
        assertEquals(killed.killingTests(), read.mutation().get(0).methods().get(0).mutants().get(0).killingTests());
        assertEquals(fileCoverage.excluded(), read.fileCoverage().excluded());
        assertEquals(fileEntry.lines().get(0), read.fileCoverage().files().get(0).lines().get(0));
    }

    @Test
    void aNullPercentMetricRoundTripsAsNullNotZero() throws IOException {
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"),
            List.of(), List.of(), List.of());

        VerdictDocument read = roundTrip(doc);

        assertNull(read.overallMetrics().jacocoLine().percent());
        assertEquals(0, read.overallMetrics().jacocoLine().denominator());
    }

    /** Truncated/malformed JSON *syntax* surfaces as Jackson's own {@code IOException} (its declared, structured failure for exactly this) - never a partially-populated document. {@link VerdictJsonReader.VerdictJsonReadException} is reserved for syntactically valid JSON with the wrong shape (see the next test). */
    @Test
    void malformedJsonSyntaxThrowsRatherThanReturningAPartialDocument() {
        assertThrows(IOException.class,
            () -> VerdictJsonReader.read(new ByteArrayInputStream("{\"schemaVersion\": \"0.1.0\"".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void aDocumentMissingARequiredFieldThrowsRatherThanGuessing() {
        String json = "{\"tool\": {\"name\": \"proof-java\", \"version\": \"0.1.0\"}}";
        assertThrows(VerdictJsonReader.VerdictJsonReadException.class,
            () -> VerdictJsonReader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }
}
