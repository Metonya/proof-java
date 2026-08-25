package dev.coverdict.analysis.mutation;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Writes a {@link MutationModuleEvidence} as JSON via Jackson's streaming
 * {@link JsonGenerator} - never a databind mapper, matching {@code
 * PerTestJsonWriter}'s style. This is the listener-to-collector wire format
 * {@link CoverdictMutationListener} writes and {@link MutationJsonReader}
 * reads back in the parent process - never the verdict schema itself.
 *
 * <p>{@link #writeMethods} is also called directly by {@code
 * dev.coverdict.analysis.report.VerdictJsonWriter} (D-55's dedup pattern for
 * {@code PerTestJsonWriter.writeEntries}): the wire format here is unsorted
 * (already deterministic from {@link MutationResultAccumulator}'s {@code
 * TreeMap}), while the verdict schema's own ordering contract means the
 * verdict writer sorts its module/method lists before calling in.
 */
public final class MutationJsonWriter {

    private static final JsonFactory FACTORY = new JsonFactory();

    private MutationJsonWriter() {
    }

    public static void write(Writer out, MutationModuleEvidence evidence) throws IOException {
        try (JsonGenerator g = FACTORY.createGenerator(out)) {
            g.writeStartObject();
            g.writeStringField("moduleId", evidence.moduleId());
            g.writeArrayFieldStart("methods");
            writeMethods(g, evidence.methods());
            g.writeEndArray();
            g.writeArrayFieldStart("warnings");
            for (AnalysisReason warning : evidence.warnings()) {
                writeWarning(g, warning);
            }
            g.writeEndArray();
            g.writeEndObject();
        }
    }

    public static void writeMethods(JsonGenerator g, List<MutatedMethod> methods) throws IOException {
        for (MutatedMethod method : methods) {
            writeMethod(g, method);
        }
    }

    private static void writeMethod(JsonGenerator g, MutatedMethod method) throws IOException {
        g.writeStartObject();
        g.writeStringField("className", method.className());
        g.writeStringField("methodName", method.methodName());
        g.writeStringField("methodDescription", method.methodDescription());
        g.writeNumberField("firstLine", method.firstLine());
        g.writeNumberField("lastLine", method.lastLine());
        g.writeArrayFieldStart("mutants");
        for (Mutant mutant : method.mutants()) {
            writeMutant(g, mutant);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeMutant(JsonGenerator g, Mutant mutant) throws IOException {
        g.writeStartObject();
        g.writeStringField("mutator", mutant.mutator());
        g.writeNumberField("line", mutant.line());
        g.writeStringField("status", mutant.status());
        g.writeArrayFieldStart("killingTests");
        for (String test : mutant.killingTests()) {
            g.writeString(test);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeWarning(JsonGenerator g, AnalysisReason warning) throws IOException {
        g.writeStartObject();
        g.writeStringField("code", warning.code());
        g.writeStringField("message", warning.message());
        writeNullableStringField(g, "path", warning.path());
        writeNullableStringField(g, "module", warning.module());
        if (warning.count() == null) {
            g.writeNullField("count");
        } else {
            g.writeNumberField("count", warning.count());
        }
        g.writeEndObject();
    }

    private static void writeNullableStringField(JsonGenerator g, String field, String value) throws IOException {
        if (value == null) {
            g.writeNullField(field);
        } else {
            g.writeStringField(field, value);
        }
    }
}
