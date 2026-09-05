package dev.proofjava.analysis.report;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import dev.proofjava.analysis.jacoco.LineCoverage;
import dev.proofjava.analysis.metrics.Metric;
import dev.proofjava.analysis.metrics.MetricSet;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.LineRange;
import dev.proofjava.analysis.mutation.MutatedMethod;
import dev.proofjava.analysis.mutation.MutationJsonWriter;
import dev.proofjava.analysis.mutation.MutationModuleEvidence;
import dev.proofjava.analysis.pertest.PerTestEntry;
import dev.proofjava.analysis.pertest.PerTestLine;
import dev.proofjava.analysis.pertest.PerTestJsonWriter;
import dev.proofjava.analysis.pertest.PerTestModuleEvidence;
import dev.proofjava.analysis.vcs.VcsIdentity;

/**
 * Writes a {@link VerdictDocument} field-by-field via Jackson's streaming
 * {@link JsonGenerator} - never string concatenation (SECURITY-POLICY.md
 * #4) - matching {@code schema/proof-verdict.schema.json} exactly,
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
    private static final String FIELD_MODULE = "module";
    private static final String FIELD_MODULES = "modules";

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
            g.writeStringField("name", "proof-java");
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

            writeInputs(g, doc);

            g.writeObjectFieldStart("coverage");
            g.writeObjectFieldStart("overall");
            writeMetricSet(g, doc.overallMetrics());
            g.writeEndObject();
            g.writeObjectFieldStart("newCode");
            if (doc.newCode().metrics() != null) {
                writeMetricSet(g, doc.newCode().metrics());
            } else {
                g.writeStringField("status", doc.newCode().unavailableStatus());
            }
            g.writeEndObject();
            g.writeEndObject();

            g.writeArrayFieldStart("changedFiles");
            List<ChangedFile> changedFiles = doc.changedFiles().stream()
                .sorted(Comparator.comparing(ChangedFile::module, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(ChangedFile::path))
                .toList();
            for (ChangedFile file : changedFiles) {
                writeChangedFile(g, file);
            }
            g.writeEndArray();

            g.writeArrayFieldStart("findings");
            List<Finding> findings = doc.findings().stream()
                .sorted(Comparator.comparing(Finding::path).thenComparingInt(Finding::startLine)
                    .thenComparing(Finding::rule).thenComparing(Finding::fingerprint))
                .toList();
            for (Finding finding : findings) {
                writeFinding(g, finding);
            }
            g.writeEndArray();

            g.writeArrayFieldStart("warnings");
            for (AnalysisReason warning : sortedReasons(doc.warnings())) {
                writeReason(g, warning);
            }
            g.writeEndArray();

            if (doc.perTest() != null) {
                writePerTest(g, doc.perTest());
            }

            if (doc.mutation() != null) {
                writeMutation(g, doc.mutation());
            }

            if (doc.fileCoverage() != null) {
                writeFileCoverage(g, doc.fileCoverage());
            }

            g.writeEndObject();
        }
    }

    /** {@code perTest} is entirely opt-in (D-46/D-55): only written when {@code --per-test-report} was set. */
    private static void writePerTest(JsonGenerator g, List<PerTestModuleEvidence> perTestModules) throws IOException {
        g.writeObjectFieldStart("perTest");
        g.writeStringField("engine", "pitest");
        g.writeStringField("engineVersion", ToolVersion.read().pitestVersion());
        g.writeArrayFieldStart(FIELD_MODULES);
        List<PerTestModuleEvidence> sorted = perTestModules.stream()
            .sorted(Comparator.comparing(PerTestModuleEvidence::moduleId))
            .toList();
        for (PerTestModuleEvidence module : sorted) {
            g.writeStartObject();
            g.writeStringField("id", module.moduleId());
            List<PerTestEntry> entries = sortedPerTestEntries(module.entries());
            List<PerTestEntry> ambient = sortedPerTestEntries(module.ambient());
            Map<String, Integer> testIds = internTestIds(entries, ambient);
            g.writeArrayFieldStart("testIds");
            for (String testId : testIds.keySet()) {
                g.writeString(testId);
            }
            g.writeEndArray();
            g.writeArrayFieldStart("entries");
            writeInternedEntries(g, entries, testIds);
            g.writeEndArray();
            g.writeArrayFieldStart("ambient");
            writeInternedEntries(g, ambient, testIds);
            g.writeEndArray();
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    /**
     * D-86: one table of test ids per module, referenced by index from each
     * line, instead of the id repeated verbatim everywhere it appears.
     *
     * <p>A PIT test id is long - a JUnit 4 vintage one runs past 200 characters -
     * and the same test covers hundreds of lines, so writing it out each time is
     * what made a real gson run produce a 235 MB {@code perTest} block inside a
     * 312 MB document, of which under 1 MB was anything a person reads.
     *
     * <p>Insertion order is the map's iteration order and the index order, and
     * the ids arrive already sorted, so the output stays byte-deterministic -
     * {@code VerdictGoldenTest} compares bytes.
     */
    private static Map<String, Integer> internTestIds(List<PerTestEntry> entries, List<PerTestEntry> ambient) {
        Map<String, Integer> ids = new LinkedHashMap<>();
        Stream.concat(entries.stream(), ambient.stream())
            .flatMap(entry -> entry.lines().stream())
            .flatMap(line -> line.tests().stream())
            .distinct()
            .sorted()
            .forEach(testId -> ids.put(testId, ids.size()));
        return ids;
    }

    private static void writeInternedEntries(JsonGenerator g, List<PerTestEntry> entries,
                                              Map<String, Integer> testIds) throws IOException {
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
                    g.writeNumber(testIds.get(test));
                }
                g.writeEndArray();
                g.writeEndObject();
            }
            g.writeEndArray();
            g.writeEndObject();
        }
    }

    /** Schema ordering rule: perTest entries/ambient sort by (className, methodName) - the shared writer itself stays unsorted (D-55). */
    private static List<PerTestEntry> sortedPerTestEntries(List<PerTestEntry> entries) {
        return entries.stream()
            .sorted(Comparator.comparing(PerTestEntry::className).thenComparing(PerTestEntry::methodName))
            .toList();
    }

    /** {@code mutation} is entirely opt-in (D-46/D-56): only written when {@code --mutation-report} was set. */
    private static void writeMutation(JsonGenerator g, List<MutationModuleEvidence> mutationModules) throws IOException {
        g.writeObjectFieldStart("mutation");
        g.writeStringField("engine", "pitest");
        g.writeStringField("engineVersion", ToolVersion.read().pitestVersion());
        g.writeArrayFieldStart(FIELD_MODULES);
        List<MutationModuleEvidence> sorted = mutationModules.stream()
            .sorted(Comparator.comparing(MutationModuleEvidence::moduleId))
            .toList();
        for (MutationModuleEvidence module : sorted) {
            g.writeStartObject();
            g.writeStringField("id", module.moduleId());
            g.writeArrayFieldStart("methods");
            MutationJsonWriter.writeMethods(g, sortedMutatedMethods(module.methods()));
            g.writeEndArray();
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    /** Schema ordering rule: mutation methods sort by (className, methodName, methodDescription) - the shared writer itself stays unsorted (D-56). */
    private static List<MutatedMethod> sortedMutatedMethods(List<MutatedMethod> methods) {
        return methods.stream()
            .sorted(Comparator.comparing(MutatedMethod::className)
                .thenComparing(MutatedMethod::methodName)
                .thenComparing(MutatedMethod::methodDescription))
            .toList();
    }

    /** {@code fileCoverage} is entirely opt-in (Plan.md Faz 1): only written when {@code --file-coverage} was set. */
    private static void writeFileCoverage(JsonGenerator g, FileCoverageBlock block) throws IOException {
        g.writeObjectFieldStart("fileCoverage");
        g.writeArrayFieldStart("files");
        List<FileCoverageEntry> sorted = block.files().stream()
            .sorted(Comparator.comparing(FileCoverageEntry::module, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FileCoverageEntry::path))
            .toList();
        for (FileCoverageEntry entry : sorted) {
            writeFileCoverageEntry(g, entry);
        }
        g.writeEndArray();
        g.writeArrayFieldStart("excluded");
        for (String path : block.excluded().stream().sorted().toList()) {
            g.writeString(path);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeFileCoverageEntry(JsonGenerator g, FileCoverageEntry entry) throws IOException {
        g.writeStartObject();
        g.writeStringField(FIELD_MODULE, entry.module());
        g.writeStringField("path", entry.path());
        g.writeObjectFieldStart("metrics");
        writeMetricSet(g, entry.metrics());
        g.writeEndObject();
        g.writeArrayFieldStart("lines");
        for (LineCoverage line : entry.lines().stream().sorted(Comparator.comparingInt(LineCoverage::number)).toList()) {
            g.writeStartArray();
            g.writeNumber(line.number());
            g.writeNumber(line.missedInstructions());
            g.writeNumber(line.coveredInstructions());
            g.writeNumber(line.missedBranches());
            g.writeNumber(line.coveredBranches());
            g.writeEndArray();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeInputs(JsonGenerator g, VerdictDocument doc) throws IOException {
        g.writeObjectFieldStart("inputs");
        g.writeStringField("diffMode", doc.diffMode());
        g.writeStringField("findingsScope", doc.findingsScope());
        VcsIdentity identity = doc.identity();
        if (identity != null && identity.baseRef() != null) {
            g.writeStringField("baseRef", identity.baseRef());
        }
        if (identity != null) {
            writeResolvedIdentity(g, identity);
        }
        g.writeNumberField("languageLevel", doc.languageLevel());
        g.writeStringField("encoding", doc.encoding());
        g.writeArrayFieldStart("exclusions");
        for (String glob : doc.exclusions()) {
            g.writeString(glob);
        }
        g.writeEndArray();
        g.writeArrayFieldStart(FIELD_MODULES);
        List<ModuleInput> modules = doc.modules().stream()
            .sorted(Comparator.comparing(ModuleInput::id))
            .toList();
        for (ModuleInput module : modules) {
            writeModule(g, module);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeResolvedIdentity(JsonGenerator g, VcsIdentity identity) throws IOException {
        g.writeObjectFieldStart("resolved");
        if (identity.base() != null) {
            g.writeStringField("base", identity.base());
        }
        if (identity.mergeBase() != null) {
            g.writeStringField("mergeBase", identity.mergeBase());
        }
        g.writeStringField("head", identity.head());
        g.writeBooleanField("dirty", identity.dirty());
        g.writeEndObject();
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

    private static void writeChangedFile(JsonGenerator g, ChangedFile file) throws IOException {
        g.writeStartObject();
        g.writeStringField("path", file.path());
        if (file.module() != null) {
            g.writeStringField(FIELD_MODULE, file.module());
        }
        g.writeStringField("classification", file.classification().schemaValue());
        // Line-count fields exist only for classification=mapped (schema note); newLines is the reliable signal for all three together.
        if (file.newLines() != null) {
            g.writeNumberField("newLines", file.newLines());
            g.writeNumberField("coveredNewLines", file.coveredNewLines());
            g.writeArrayFieldStart("uncoveredNewRanges");
            for (LineRange range : file.uncoveredNewRanges()) {
                g.writeStartArray();
                g.writeNumber(range.start());
                g.writeNumber(range.end());
                g.writeEndArray();
            }
            g.writeEndArray();
        }
        g.writeEndObject();
    }

    private static void writeFinding(JsonGenerator g, Finding finding) throws IOException {
        g.writeStartObject();
        g.writeStringField("rule", finding.rule());
        g.writeStringField("severity", finding.severity().name());
        g.writeStringField("confidence", finding.confidence().name());
        g.writeStringField(FIELD_MODULE, finding.module());
        g.writeStringField("path", finding.path());
        g.writeNumberField("startLine", finding.startLine());
        g.writeNumberField("endLine", finding.endLine());
        if (finding.testMethod() != null) {
            g.writeStringField("testMethod", finding.testMethod());
        }
        if (finding.productionMethod() != null) {
            g.writeStringField("productionMethod", finding.productionMethod());
        }
        if (finding.relatedTestMethod() != null) {
            g.writeStringField("relatedTestMethod", finding.relatedTestMethod());
        }
        if (finding.relatedPath() != null) {
            g.writeStringField("relatedPath", finding.relatedPath());
        }
        g.writeStringField("message", finding.message());
        g.writeStringField("suggestedAction", finding.suggestedAction());
        g.writeStringField("fingerprint", finding.fingerprint());
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
        if (reason.count() != null) {
            g.writeNumberField("count", reason.count());
        }
        if (reason.module() != null) {
            g.writeStringField(FIELD_MODULE, reason.module());
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
