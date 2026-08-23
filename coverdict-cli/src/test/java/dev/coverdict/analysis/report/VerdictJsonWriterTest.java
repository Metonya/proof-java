package dev.coverdict.analysis.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

import dev.coverdict.analysis.metrics.MetricsEngine;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ResolvedSourceFile;

/** Asserts real analyzer output against the checked-in contract (schema/coverdict-verdict.schema.json), not just a spot-checked example. */
class VerdictJsonWriterTest {

    private static final Path SCHEMA_FILE = Path.of("../schema/coverdict-verdict.schema.json");

    private JsonSchema schema() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012);
        try (var in = Files.newInputStream(SCHEMA_FILE)) {
            return factory.getSchema(in);
        }
    }

    private JsonNode toNode(byte[] json) throws IOException {
        return new ObjectMapper().readTree(json);
    }

    private VerdictDocument completeDocument() {
        List<ResolvedSourceFile> files = List.of(
            new ResolvedSourceFile("root", "src/main/java/com/example/Calc.java",
                List.of(
                    new dev.coverdict.analysis.jacoco.LineCoverage(10, 0, 3, 0, 0),
                    new dev.coverdict.analysis.jacoco.LineCoverage(11, 2, 1, 0, 0)),
                1, 1, true));
        var metrics = MetricsEngine.computeOverall(files);
        var module = new ModuleInput("root", ".", List.of("src/main/java"), List.of("src/test/java"),
            List.of(new ReportInput("jacoco.xml", "unverified")));
        return new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module), metrics,
            List.of(new AnalysisReason("MISSING_SOURCE_FILE", "not found", "src/main/java/Missing.java", "root")));
    }

    private VerdictDocument incompleteDocument() {
        return new VerdictDocument("0.1.0", "0.1.0-TEST", false,
            List.of(new AnalysisReason("MALFORMED_JACOCO_XML", "bad xml")),
            17, "UTF-8", List.of(), List.of(), MetricsEngine.computeOverall(List.of()), List.of());
    }

    @Test
    void completeDocumentValidatesAgainstTheSchema() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, completeDocument());

        Set<ValidationMessage> errors = schema().validate(toNode(out.toByteArray()));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void incompleteDocumentValidatesAgainstTheSchema() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, incompleteDocument());

        Set<ValidationMessage> errors = schema().validate(toNode(out.toByteArray()));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void twoRunsOnTheSameInputProduceByteIdenticalOutput() throws IOException {
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        VerdictJsonWriter.write(first, completeDocument());
        VerdictJsonWriter.write(second, completeDocument());
        assertArrayEquals(first.toByteArray(), second.toByteArray());
    }

    @Test
    void usesLineFeedNotSystemLineSeparator() throws IOException {
        // The Windows trap this class's javadoc calls out: Jackson's default
        // pretty printer uses System.lineSeparator() unless overridden.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VerdictJsonWriter.write(out, completeDocument());
        String json = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\n"), "expected LF-separated output");
        assertTrue(!json.contains("\r"), "must not contain CR - byte-determinism requires LF only, not System.lineSeparator()");
    }
}
