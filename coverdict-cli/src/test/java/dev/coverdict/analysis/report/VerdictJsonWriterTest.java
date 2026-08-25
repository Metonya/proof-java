package dev.coverdict.analysis.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

import dev.coverdict.analysis.jacoco.LineCoverage;
import dev.coverdict.analysis.metrics.MetricsEngine;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.LineRange;
import dev.coverdict.analysis.model.ResolvedSourceFile;
import dev.coverdict.analysis.model.Severity;
import dev.coverdict.analysis.vcs.VcsIdentity;

/** Asserts real analyzer output against the checked-in contract (schema/coverdict-verdict.schema.json), not just a spot-checked example. */
class VerdictJsonWriterTest {

    private static final Path SCHEMA_FILE = Path.of("../schema/coverdict-verdict.schema.json");
    private static final String SHA_A = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678";
    private static final String SHA_B = "0f1e2d3c4b5a69788796a5b4c3d2e1f001234567";

    private JsonSchema schema() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012);
        try (var in = Files.newInputStream(SCHEMA_FILE)) {
            return factory.getSchema(in);
        }
    }

    private JsonNode toNode(byte[] json) throws IOException {
        return new ObjectMapper().readTree(json);
    }

    private ModuleInput module() {
        return new ModuleInput("root", ".", List.of("src/main/java"), List.of("src/test/java"),
            List.of(new ReportInput("jacoco.xml", "unverified")));
    }

    private VerdictDocument noVcsCompleteDocument() {
        List<ResolvedSourceFile> files = List.of(
            new ResolvedSourceFile("root", "src/main/java/com/example/Calc.java",
                List.of(new LineCoverage(10, 0, 3, 0, 0), new LineCoverage(11, 2, 1, 0, 0)),
                1, 1, true));
        var metrics = MetricsEngine.compute(files);
        return new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, metrics,
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(),
            List.of(new AnalysisReason("MISSING_SOURCE_FILE", "not found", "src/main/java/Missing.java", "root")));
    }

    private VerdictDocument noVcsIncompleteDocument() {
        return new VerdictDocument("0.1.0", "0.1.0-TEST", false,
            List.of(new AnalysisReason("MALFORMED_JACOCO_XML", "bad xml")),
            17, "UTF-8", List.of(), List.of(), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of());
    }

    /** Mirrors schema/examples/golden-complete.json's shape: base-ref mode, real newCode, a mix of changedFiles classifications. */
    private VerdictDocument baseRefCompleteDocument() {
        List<ResolvedSourceFile> overallFiles = List.of(
            new ResolvedSourceFile("root", "src/main/java/com/example/Calc.java",
                List.of(new LineCoverage(10, 0, 3, 0, 0), new LineCoverage(11, 2, 1, 0, 0)),
                1, 1, true));
        var overall = MetricsEngine.compute(overallFiles);

        List<ResolvedSourceFile> newCodeFiles = List.of(
            new ResolvedSourceFile("root", "src/main/java/com/example/Calc.java",
                List.of(new LineCoverage(11, 2, 1, 0, 0)), 0, 0, true));
        var newCode = MetricsEngine.compute(newCodeFiles);

        VcsIdentity identity = new VcsIdentity(SHA_B, "origin/main", SHA_A, SHA_A, true);
        List<ChangedFile> changedFiles = List.of(
            new ChangedFile("src/main/java/com/example/Calc.java", "root", Classification.MAPPED,
                1, 0, List.of(new LineRange(11, 11))),
            new ChangedFile("src/main/java/com/example/package-info.java", "root", Classification.NON_EXECUTABLE,
                null, null, List.of()),
            new ChangedFile("build-tools/Outside.java", null, Classification.UNKNOWN, null, null, List.of()));

        List<Finding> findings = List.of(
            new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
                "src/test/java/com/example/CalcTest.java", 10, 14,
                "com.example.CalcTest#printsResultOnly()",
                "Test 'printsResultOnly' contains no recognized assertion, verification, or expected exception.",
                "Add an assertion on the observed behavior, or register the helper as a custom oracle in configuration.",
                "3f9a1c2b4d5e6a70"));

        return new VerdictDocument("0.1.0", "0.1.0-TEST", false,
            List.of(new AnalysisReason("CHANGED_JAVA_OUTSIDE_MODULES", "outside every module", "build-tools/Outside.java")),
            17, "UTF-8", List.of(), List.of(module()), "base-ref", "all", identity, overall,
            NewCodeCoverage.available(newCode), changedFiles, findings,
            List.of(new AnalysisReason("UNTRACKED_NON_JAVA_FILE", "Untracked file ignored by diff analysis.", "notes.txt")));
    }

    private VerdictDocument workingTreeDocument() {
        VcsIdentity identity = new VcsIdentity(SHA_B, null, null, null, false);
        return new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "working-tree", "changed", identity,
            MetricsEngine.compute(List.of()), NewCodeCoverage.available(MetricsEngine.compute(List.of())),
            List.of(), List.of(), List.of());
    }

    private VerdictDocument documentWithPerTest() {
        VerdictDocument base = workingTreeDocument();
        dev.coverdict.analysis.pertest.PerTestLine line = new dev.coverdict.analysis.pertest.PerTestLine(41,
            List.of("com.demo.core.IbanValidatorTest#acceptsAValidGermanIban()"));
        dev.coverdict.analysis.pertest.PerTestEntry entry = new dev.coverdict.analysis.pertest.PerTestEntry(
            "com.demo.core.IbanValidator", "validate", List.of(line));
        dev.coverdict.analysis.pertest.PerTestEntry ambient = new dev.coverdict.analysis.pertest.PerTestEntry(
            "com.demo.core.IbanValidator", "<clinit>", List.of(new dev.coverdict.analysis.pertest.PerTestLine(12,
                List.of("com.demo.core.IbanValidatorTest#acceptsAValidGermanIban()"))));
        dev.coverdict.analysis.pertest.PerTestModuleEvidence moduleEvidence =
            new dev.coverdict.analysis.pertest.PerTestModuleEvidence("root", List.of(entry), List.of(ambient));
        return new VerdictDocument(base.schemaVersion(), base.toolVersion(), base.complete(), base.incompleteReasons(),
            base.languageLevel(), base.encoding(), base.exclusions(), base.modules(), base.diffMode(),
            base.findingsScope(), base.identity(), base.overallMetrics(), base.newCode(), base.changedFiles(),
            base.findings(), base.warnings(), List.of(moduleEvidence));
    }

    @Test
    void perTestDocumentValidatesAgainstTheSchema() throws IOException {
        assertValid(documentWithPerTest());
    }

    @Test
    void perTestFieldIsEntirelyAbsentWhenNull() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, workingTreeDocument());
        JsonNode doc = toNode(out.toByteArray());
        assertTrue(doc.at("/perTest").isMissingNode(), "perTest must be omitted, not written as null");
    }

    @Test
    void noVcsCompleteDocumentValidatesAgainstTheSchema() throws IOException {
        assertValid(noVcsCompleteDocument());
    }

    @Test
    void noVcsIncompleteDocumentValidatesAgainstTheSchema() throws IOException {
        assertValid(noVcsIncompleteDocument());
    }

    @Test
    void baseRefDocumentValidatesAgainstTheSchema() throws IOException {
        assertValid(baseRefCompleteDocument());
    }

    @Test
    void workingTreeDocumentValidatesAgainstTheSchema() throws IOException {
        assertValid(workingTreeDocument());
    }

    @Test
    void workingTreeModeNeverWritesBaseRefOrResolvedBaseFields() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, workingTreeDocument());
        JsonNode doc = toNode(out.toByteArray());

        assertTrue(doc.at("/inputs/baseRef").isMissingNode(), "working-tree mode must not write inputs.baseRef");
        assertTrue(doc.at("/inputs/resolved/base").isMissingNode(), "working-tree mode must not write inputs.resolved.base");
        assertTrue(doc.at("/inputs/resolved/mergeBase").isMissingNode(), "working-tree mode must not write inputs.resolved.mergeBase");
        assertFalse(doc.at("/inputs/resolved/head").isMissingNode(), "working-tree mode must still write inputs.resolved.head");
    }

    @Test
    void unknownChangedFileOutsideEveryModuleOmitsTheModuleField() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, baseRefCompleteDocument());
        JsonNode doc = toNode(out.toByteArray());

        JsonNode outside = null;
        for (JsonNode entry : doc.at("/changedFiles")) {
            if ("build-tools/Outside.java".equals(entry.at("/path").asText())) {
                outside = entry;
            }
        }
        assertNotNull(outside, "expected the outside-every-module changedFile entry");
        assertTrue(outside.at("/module").isMissingNode(), "module must be omitted, never null, for an unmapped path");
        assertFalse(outside.has("newLines"), "unknown classification carries no line-count fields");
    }

    @Test
    void twoRunsOnTheSameInputProduceByteIdenticalOutput() throws IOException {
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        VerdictJsonWriter.write(first, baseRefCompleteDocument());
        VerdictJsonWriter.write(second, baseRefCompleteDocument());
        assertArrayEquals(first.toByteArray(), second.toByteArray());
    }

    @Test
    void usesLineFeedNotSystemLineSeparator() throws IOException {
        // The Windows trap this class's javadoc calls out: Jackson's default
        // pretty printer uses System.lineSeparator() unless overridden.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, noVcsCompleteDocument());
        String json = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\n"), "expected LF-separated output");
        assertTrue(!json.contains("\r"), "must not contain CR - byte-determinism requires LF only, not System.lineSeparator()");
    }

    private void assertValid(VerdictDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, doc);
        Set<ValidationMessage> errors = schema().validate(toNode(out.toByteArray()));
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
