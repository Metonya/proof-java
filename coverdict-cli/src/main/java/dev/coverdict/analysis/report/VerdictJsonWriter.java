package dev.coverdict.analysis.report;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import dev.coverdict.analysis.metrics.Metric;
import dev.coverdict.analysis.metrics.MetricSet;
import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Writes a {@link VerdictDocument} field-by-field via Jackson's streaming
 * {@link JsonGenerator} - never string concatenation (SECURITY-POLICY.md
 * #4) - matching {@code schema/coverdict-verdict.schema.json} exactly,
 * including its declared field-ordering rules, so two runs on the same
 * input are byte-identical (hard rule: "Stable deterministic ordering").
 *
 * <p>Two Windows-specific traps this deliberately avoids: Jackson's default
 * pretty printer uses {@code System.lineSeparator()} (CRLF here) - overridden
 * to LF below - and {@link Metric#percent()} is a {@link BigDecimal}, whose
 * {@code toString()}/{@code writeNumber} is locale-independent, unlike
 * {@code String.format} on this machine's tr_TR default locale.
 */
public final class VerdictJsonWriter {

    private static final JsonFactory FACTORY = new JsonFactory();

    private VerdictJsonWriter() {
    }

    public static void write(OutputStream out, VerdictDocument doc) throws IOException {
        try (JsonGenerator g = FACTORY.createGenerator(out, JsonEncoding.UTF8)) {
            DefaultIndenter lfIndenter = new DefaultIndenter("  ", "\n");
            DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
            pp.indentObjectsWith(lfIndenter);
            pp.indentArraysWith(lfIndenter);
            g.setPrettyPrinter(pp);

            g.writeStartObject();
            g.writeStringField("schemaVersion", doc.schemaVersion());

            g.writeObjectFieldStart("tool");
            g.writeStringField("name", "coverdict");
            g.writeStringField("version", doc.toolVersion());
            g.writeEndObject();

            g.writeObjectFieldStart("analysis");
            g.writeStringField("status", doc.complete() ? "complete" : "incomplete");
            g.writeNumberField("exitCode", doc.complete() ? 0 : 3);
            g.writeArrayFieldStart("incompleteReasons");
            for (AnalysisReason reason : sortedReasons(doc.incompleteReasons())) {
                writeReason(g, reason);
            }
            g.writeEndArray();
            g.writeEndObject();

            g.writeObjectFieldStart("inputs");
            g.writeStringField("diffMode", "no-vcs");
            g.writeNumberField("languageLevel", doc.languageLevel());
            g.writeStringField("encoding", doc.encoding());
            g.writeArrayFieldStart("exclusions");
            for (String glob : doc.exclusions()) {
                g.writeString(glob);
            }
            g.writeEndArray();
            g.writeArrayFieldStart("modules");
            List<ModuleInput> modules = doc.modules().stream()
                .sorted(Comparator.comparing(ModuleInput::id))
                .toList();
            for (ModuleInput module : modules) {
                writeModule(g, module);
            }
            g.writeEndArray();
            g.writeEndObject();

            g.writeObjectFieldStart("coverage");
            g.writeObjectFieldStart("overall");
            writeMetricSet(g, doc.overallMetrics());
            g.writeEndObject();
            g.writeObjectFieldStart("newCode");
            g.writeStringField("status", "unavailable_no_vcs");
            g.writeEndObject();
            g.writeEndObject();

            g.writeArrayFieldStart("changedFiles");
            g.writeEndArray(); // no-vcs mode has no diff, so this is always empty (schema note)

            g.writeArrayFieldStart("findings");
            g.writeEndArray(); // L0 oracle rules are M1b, not this step

            g.writeArrayFieldStart("warnings");
            for (AnalysisReason warning : sortedReasons(doc.warnings())) {
                writeReason(g, warning);
            }
            g.writeEndArray();

            g.writeEndObject();
        }
    }

    private static void writeModule(JsonGenerator g, ModuleInput module) throws IOException {
        g.writeStartObject();
        g.writeStringField("id", module.id());
        g.writeStringField("root", module.root());
        g.writeArrayFieldStart("sourceRoots");
        for (String s : module.sourceRoots()) {
            g.writeString(s);
        }
        g.writeEndArray();
        g.writeArrayFieldStart("testRoots");
        for (String t : module.testRoots()) {
            g.writeString(t);
        }
        g.writeEndArray();
        g.writeArrayFieldStart("reports");
        for (ReportInput report : module.reports()) {
            g.writeStartObject();
            g.writeStringField("path", report.path());
            g.writeStringField("freshness", report.freshness());
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeMetricSet(JsonGenerator g, MetricSet metrics) throws IOException {
        writeMetric(g, "jacoco-line", metrics.jacocoLine());
        writeMetric(g, "strict-line", metrics.strictLine());
        writeMetric(g, "sonar-compatible", metrics.sonarCompatible());
    }

    private static void writeMetric(JsonGenerator g, String fieldName, Metric metric) throws IOException {
        g.writeObjectFieldStart(fieldName);
        g.writeStringField("numeratorName", metric.numeratorName());
        g.writeNumberField("numerator", metric.numerator());
        g.writeStringField("denominatorName", metric.denominatorName());
        g.writeNumberField("denominator", metric.denominator());
        BigDecimal percent = metric.percent();
        if (percent == null) {
            g.writeNullField("percent");
        } else {
            g.writeFieldName("percent");
            g.writeNumber(percent);
        }
        g.writeEndObject();
    }

    private static void writeReason(JsonGenerator g, AnalysisReason reason) throws IOException {
        g.writeStartObject();
        g.writeStringField("code", reason.code());
        g.writeStringField("message", reason.message());
        if (reason.path() != null) {
            g.writeStringField("path", reason.path());
        }
        if (reason.module() != null) {
            g.writeStringField("module", reason.module());
        }
        g.writeEndObject();
    }

    /** Schema ordering rule: warnings/incompleteReasons sort by (code, path, message). */
    private static List<AnalysisReason> sortedReasons(List<AnalysisReason> reasons) {
        Comparator<String> nullsFirst = Comparator.nullsFirst(Comparator.naturalOrder());
        return reasons.stream()
            .sorted(Comparator.comparing(AnalysisReason::code)
                .thenComparing(AnalysisReason::path, nullsFirst)
                .thenComparing(AnalysisReason::message))
            .toList();
    }
}
