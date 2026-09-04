package dev.proofjava.analysis.pertest;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Writes a {@link PerTestModuleEvidence} as JSON via Jackson's streaming
 * {@link JsonGenerator} - never a databind mapper (this codebase never
 * depends on jackson-databind, matching {@code VerdictJsonWriter}'s
 * deterministic field-by-field style). This is the exporter-to-driver wire
 * format {@link CoverdictLineExporter} writes and {@link PerTestJsonReader}
 * reads back in the parent process - never the verdict schema itself.
 *
 * <p>{@link #writeEntries} is also called directly by {@code
 * dev.proofjava.analysis.report.VerdictJsonWriter} (a different, larger
 * consumer of the same {@link PerTestEntry}/{@link PerTestLine} shape) so
 * the {@code className}/{@code methodName}/{@code lines}/{@code tests}
 * nesting is written in exactly one place - the wire format here is
 * unsorted (already deterministic from {@link BlockLineResolver}), while
 * the verdict schema's own ordering contract means the verdict writer
 * sorts its list before calling in.
 */
public final class PerTestJsonWriter {

    private static final JsonFactory FACTORY = new JsonFactory();

    private PerTestJsonWriter() {
    }

    public static void write(Writer out, PerTestModuleEvidence evidence) throws IOException {
        try (JsonGenerator g = FACTORY.createGenerator(out)) {
            g.writeStartObject();
            g.writeStringField("moduleId", evidence.moduleId());
            g.writeArrayFieldStart("entries");
            writeEntries(g, evidence.entries());
            g.writeEndArray();
            g.writeArrayFieldStart("ambient");
            writeEntries(g, evidence.ambient());
            g.writeEndArray();
            g.writeEndObject();
        }
    }

    public static void writeEntries(JsonGenerator g, List<PerTestEntry> entries) throws IOException {
        for (PerTestEntry entry : entries) {
            g.writeStartObject();
            g.writeStringField("className", entry.className());
            g.writeStringField("methodName", entry.methodName());
            g.writeArrayFieldStart("lines");
            for (PerTestLine line : entry.lines()) {
                g.writeStartObject();
                g.writeNumberField("line", line.line());
                g.writeArrayFieldStart("tests");
                for (String test : line.tests()) {
                    g.writeString(test);
                }
                g.writeEndArray();
                g.writeEndObject();
            }
            g.writeEndArray();
            g.writeEndObject();
        }
    }
}
