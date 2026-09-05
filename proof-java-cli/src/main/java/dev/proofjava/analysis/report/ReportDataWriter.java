package dev.proofjava.analysis.report;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import dev.proofjava.analysis.metrics.Metric;
import dev.proofjava.analysis.metrics.MetricSet;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.LineRange;
import dev.proofjava.analysis.mutation.Mutant;
import dev.proofjava.analysis.mutation.MutatedMethod;
import dev.proofjava.analysis.mutation.MutationModuleEvidence;
import dev.proofjava.analysis.pertest.PerTestEntry;
import dev.proofjava.analysis.pertest.PerTestModuleEvidence;
import dev.proofjava.analysis.vcs.VcsIdentity;

/**
 * D-80/D-81: the HTML report's presentation data, as one JSON object
 * {@link HtmlRenderer} embeds verbatim in a {@code <script
 * type="application/json">} for the report's own client-side script to
 * read. Separate from {@link VerdictJsonWriter} on purpose - that one
 * writes the schema-contract document (hard rule 7); this one writes
 * display-shaped data (Turkish-formatted numbers, a friendly-name lookup
 * for only the codes this exact report uses, a flat source-root-relative
 * file list) that has no business in the verdict JSON schema.
 *
 * <p>Reads only what {@code doc} already carries and never computes a new
 * metric: every numerator/denominator pair below is copied verbatim from an
 * already-computed {@link dev.proofjava.analysis.metrics.Metric} (hard rule
 * 4) - nothing here re-derives a percentage from anything but that metric's
 * own numerator/denominator, with the same scale-1/HALF_UP rule {@link
 * dev.proofjava.analysis.metrics.Metric#of} used to produce it.
 */
final class ReportDataWriter {

    private static final Locale REPORT_LOCALE = Locale.ENGLISH;
    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", REPORT_LOCALE);
    private static final JsonFactory FACTORY = new JsonFactory();
    private static final String FIELD_MODULE = "module";
    private static final String FIELD_SEARCH = "search";

    private ReportDataWriter() {
    }

    static String write(VerdictDocument doc) {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = FACTORY.createGenerator(sw)) {
            Set<String> used = new LinkedHashSet<>();
            g.writeStartObject();
            writeMeta(g, doc);
            writeRuleIds(g, used);
            writeCoverage(g, doc);
            writeChangedFiles(g, doc, used);
            writeFindings(g, doc, used);
            writeReasonList(g, "warnings", doc.warnings(), used);
            writeReasonList(g, "incompleteReasons", doc.incompleteReasons(), used);
            if (doc.perTest() != null) {
                writePerTest(g, doc.perTest());
            }
            if (doc.mutation() != null) {
                Map<String, Integer> testIds = internKillingTests(doc.mutation());
                // D-90: the report shows labels, not engine ids. The raw id
                // stays in the verdict JSON, which is the machine contract.
                g.writeArrayFieldStart("testIds");
                for (String testId : testIds.keySet()) {
                    g.writeString(TestLabels.readable(testId));
                }
                g.writeEndArray();
                writeMutation(g, doc.mutation(), used, testIds);
            }
            if (doc.fileCoverage() != null) {
                writeFileCoverage(g, doc.fileCoverage(), doc.modules());
            }
            writeLabels(g, used);
            g.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sw.toString();
    }

    // ---- Meta / run identity ----

    private static void writeMeta(JsonGenerator g, VerdictDocument doc) throws IOException {
        g.writeObjectFieldStart("meta");
        g.writeStringField("toolVersion", doc.toolVersion());
        g.writeStringField("schemaVersion", doc.schemaVersion());
        g.writeStringField("generatedAt", GENERATED_AT_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault())));
        g.writeBooleanField("complete", doc.complete());
        g.writeStringField("statusLabel", doc.complete() ? "complete" : "incomplete");
        g.writeStringField("modules", moduleSummary(doc.modules()));
        g.writeStringField("diffMode", diffModeLabel(doc.diffMode()));
        VcsIdentity identity = doc.identity();
        g.writeArrayFieldStart("commitRows");
        if (identity != null) {
            writeCommitRows(g, identity);
        }
        g.writeEndArray();
        if (identity != null) {
            if (identity.baseRef() != null) {
                g.writeStringField("baseRef", identity.baseRef());
            }
            g.writeBooleanField("dirty", identity.dirty());
        }
        g.writeNumberField("languageLevel", doc.languageLevel());
        g.writeStringField("encoding", doc.encoding());
        g.writeStringField("findingsScope", doc.findingsScope());
        g.writeStringField("findingsScopeLabel", findingsScopeLabel(doc.findingsScope()));
        g.writeArrayFieldStart("exclusions");
        for (String excl : doc.exclusions()) {
            g.writeString(excl);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    /** Base/merge-base/HEAD collapse into one row when they're all the same commit - see {@code HtmlRenderer}'s D-79 history for why (three identical 40-char hashes stacked said nothing a reader could use). */
    private static void writeCommitRows(JsonGenerator g, VcsIdentity identity) throws IOException {
        Set<String> distinct = new LinkedHashSet<>();
        if (identity.base() != null) {
            distinct.add(identity.base());
        }
        if (identity.mergeBase() != null) {
            distinct.add(identity.mergeBase());
        }
        distinct.add(identity.head());
        if (distinct.size() == 1) {
            writeCommitRow(g, "Commit", distinct.iterator().next());
            return;
        }
        if (identity.base() != null) {
            writeCommitRow(g, "Base commit", identity.base());
        }
        if (identity.mergeBase() != null) {
            writeCommitRow(g, "Merge-base", identity.mergeBase());
        }
        writeCommitRow(g, "HEAD", identity.head());
    }

    private static void writeCommitRow(JsonGenerator g, String label, String sha) throws IOException {
        g.writeStartObject();
        g.writeStringField("label", label);
        g.writeStringField("short", sha.length() > 7 ? sha.substring(0, 7) : sha);
        g.writeStringField("full", sha);
        g.writeEndObject();
    }

    private static String moduleSummary(List<ModuleInput> modules) {
        if (modules.isEmpty()) {
            return "—";
        }
        return modules.stream().map(m -> m.id() + " (" + m.root() + ")")
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String diffModeLabel(String diffMode) {
        return switch (diffMode) {
            case "no-vcs" -> "no diff computed (no-vcs)";
            case "working-tree" -> "uncommitted changes (working tree)";
            case "base-ref" -> "diff against the given ref (base-ref)";
            default -> diffMode;
        };
    }

    private static String findingsScopeLabel(String findingsScope) {
        return "changed".equals(findingsScope) ? "changed test files only" : "all test files";
    }

    /** The full v0.1 rule catalog, sorted, always written in full - a rule with zero findings in this run still gets a soft "0" chip in the report (D-80: a zero is information), which needs to know the rule exists at all, not just the ones that fired. */
    private static void writeRuleIds(JsonGenerator g, Set<String> used) throws IOException {
        g.writeArrayFieldStart("ruleIds");
        List<String> sorted = new ArrayList<>(dev.proofjava.analysis.model.RuleIds.ALL);
        Collections.sort(sorted);
        for (String rule : sorted) {
            used.add(rule);
            g.writeString(rule);
        }
        g.writeEndArray();
    }

    // ---- Coverage ----

    private static void writeCoverage(JsonGenerator g, VerdictDocument doc) throws IOException {
        g.writeObjectFieldStart("coverage");
        g.writeArrayFieldStart("overall");
        writeMetricSet(g, doc.overallMetrics());
        g.writeEndArray();
        g.writeObjectFieldStart("newCode");
        if (doc.newCode().metrics() != null) {
            g.writeBooleanField("available", true);
            g.writeArrayFieldStart("metrics");
            writeMetricSet(g, doc.newCode().metrics());
            g.writeEndArray();
        } else {
            g.writeBooleanField("available", false);
            g.writeStringField("unavailableStatus", doc.newCode().unavailableStatus());
        }
        g.writeEndObject();
        g.writeEndObject();
    }

    private static void writeMetricSet(JsonGenerator g, MetricSet metrics) throws IOException {
        writeMetric(g, "jacoco-line", metrics.jacocoLine());
        writeMetric(g, "strict-line", metrics.strictLine());
        writeMetric(g, "sonar-compatible", metrics.sonarCompatible());
    }

    private static void writeMetric(JsonGenerator g, String mode, Metric metric) throws IOException {
        g.writeStartObject();
        g.writeStringField("mode", mode);
        BigDecimal percent = metric.percent();
        g.writeStringField("pctText", formatPercent(percent));
        if (percent == null) {
            g.writeNullField("pct");
        } else {
            g.writeNumberField("pct", percent);
        }
        g.writeNumberField("numerator", metric.numerator());
        g.writeNumberField("denominator", metric.denominator());
        g.writeStringField("numeratorText", formatInt(metric.numerator()));
        g.writeStringField("denominatorText", formatInt(metric.denominator()));
        g.writeStringField("numeratorName", metric.numeratorName());
        g.writeStringField("denominatorName", metric.denominatorName());
        g.writeEndObject();
    }

    private static String formatPercent(BigDecimal percent) {
        return percent == null ? "n/a" : percent + "%";
    }

    private static String formatInt(int n) {
        return String.format(REPORT_LOCALE, "%,d", n);
    }

    // ---- Changed files (D-80 fix: section is always present, even when empty - hard rule 3a) ----

    private static void writeChangedFiles(JsonGenerator g, VerdictDocument doc, Set<String> used) throws IOException {
        g.writeArrayFieldStart("changedFiles");
        for (ChangedFile file : doc.changedFiles()) {
            used.add(file.classification().schemaValue());
            g.writeStartObject();
            g.writeStringField(FIELD_MODULE, file.module());
            g.writeStringField("path", file.path());
            g.writeStringField("classification", file.classification().schemaValue());
            g.writeStringField(FIELD_SEARCH,
                joinNonBlank(file.module(), file.path(), file.classification().schemaValue()).toLowerCase(Locale.ROOT));
            if (file.newLines() != null) {
                g.writeNumberField("newLines", file.newLines());
                g.writeNumberField("coveredNewLines", file.coveredNewLines());
                g.writeStringField("uncoveredRanges", formatRanges(file.uncoveredNewRanges()));
            }
            g.writeEndObject();
        }
        g.writeEndArray();
    }

    private static String formatRanges(List<LineRange> ranges) {
        StringBuilder sb = new StringBuilder();
        for (LineRange r : ranges) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(r.start() == r.end() ? String.valueOf(r.start()) : r.start() + "-" + r.end());
        }
        return sb.isEmpty() ? "-" : sb.toString();
    }

    // ---- Findings (grouped by rule client-side; server sorts, escapes, and builds the search blob) ----

    private static void writeFindings(JsonGenerator g, VerdictDocument doc, Set<String> used) throws IOException {
        g.writeObjectFieldStart("findings");
        g.writeStringField("scope", doc.findingsScope());
        g.writeStringField("scopeLabel", findingsScopeLabel(doc.findingsScope()));
        g.writeArrayFieldStart("items");
        List<Finding> sorted = doc.findings().stream()
            .sorted(Comparator.comparing(Finding::rule).thenComparing(Finding::path)
                .thenComparingInt(Finding::startLine))
            .toList();
        for (Finding f : sorted) {
            used.add(f.rule());
            used.add(f.severity().name());
            used.add(f.confidence().name());
            String anchorMethod = f.testMethod() != null ? f.testMethod() : f.productionMethod();
            g.writeStartObject();
            g.writeStringField("rule", f.rule());
            g.writeStringField("severity", f.severity().name());
            g.writeStringField("confidence", f.confidence().name());
            g.writeStringField("path", f.path());
            g.writeNumberField("startLine", f.startLine());
            g.writeNumberField("endLine", f.endLine());
            if (anchorMethod != null) {
                g.writeStringField("anchorMethod", anchorMethod);
            }
            g.writeStringField("message", f.message());
            g.writeStringField("suggestedAction", f.suggestedAction());
            if (f.fingerprint() != null) {
                g.writeStringField("fingerprint", f.fingerprint());
            }
            g.writeStringField(FIELD_SEARCH, joinNonBlank(f.rule(), f.severity().name(), f.confidence().name(), f.path(),
                anchorMethod, f.message(), f.suggestedAction()).toLowerCase(Locale.ROOT));
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    /** Joins non-null, non-blank parts with a single space - unlike {@code String.join}, an absent optional field never leaves a stray double space in the built search text. */
    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    // ---- Warnings / incomplete reasons ----

    private static void writeReasonList(JsonGenerator g, String field, List<AnalysisReason> reasons, Set<String> used)
        throws IOException {
        g.writeArrayFieldStart(field);
        for (AnalysisReason r : reasons) {
            used.add(r.code());
            g.writeStartObject();
            g.writeStringField("code", r.code());
            g.writeStringField("message", r.message());
            if (r.path() != null) {
                g.writeStringField("path", r.path());
            }
            if (r.module() != null) {
                g.writeStringField(FIELD_MODULE, r.module());
            }
            if (r.count() != null) {
                g.writeNumberField("count", r.count());
            }
            g.writeStringField(FIELD_SEARCH, joinNonBlank(r.code(), r.message(), r.path()).toLowerCase(Locale.ROOT));
            g.writeEndObject();
        }
        g.writeEndArray();
    }

    // ---- Test-based evidence (L2) ----

    private static void writePerTest(JsonGenerator g, List<PerTestModuleEvidence> perTest) throws IOException {
        g.writeArrayFieldStart("perTest");
        for (PerTestModuleEvidence module : perTest) {
            g.writeStartObject();
            g.writeStringField("moduleId", module.moduleId());
            g.writeNumberField("entryLineCount", countLines(module.entries()));
            g.writeNumberField("ambientLineCount", countLines(module.ambient()));
            g.writeEndObject();
        }
        g.writeEndArray();
    }

    private static int countLines(List<PerTestEntry> entries) {
        return entries.stream().mapToInt(e -> e.lines().size()).sum();
    }

    // ---- Mutation (L3): per-status counts (no "other" catch-all bucket - D-80 fix), simplified signatures ----

    /**
     * D-86: the report embeds its data as JSON in the page, so it pays the same
     * repetition cost the verdict did - a gson report was 67 MB, nearly all of
     * it the same killing-test ids written once per mutant. One table for the
     * whole report, indexes everywhere else; the page's script resolves them.
     */
    private static Map<String, Integer> internKillingTests(List<MutationModuleEvidence> mutation) {
        Map<String, Integer> ids = new LinkedHashMap<>();
        mutation.stream()
            .flatMap(module -> module.methods().stream())
            .flatMap(method -> method.mutants().stream())
            .flatMap(mutant -> mutant.killingTests().stream())
            .distinct()
            .sorted()
            .forEach(testId -> ids.put(testId, ids.size()));
        return ids;
    }

    private static void writeMutation(JsonGenerator g, List<MutationModuleEvidence> mutation, Set<String> used,
                                       Map<String, Integer> testIds)
        throws IOException {
        g.writeObjectFieldStart("mutation");

        Map<String, Long> totalByStatus = new LinkedHashMap<>();
        for (MutationModuleEvidence module : mutation) {
            for (MutatedMethod method : module.methods()) {
                for (Mutant m : method.mutants()) {
                    totalByStatus.merge(m.status(), 1L, Long::sum);
                }
            }
        }
        g.writeObjectFieldStart("totalsByStatus");
        for (Map.Entry<String, Long> e : totalByStatus.entrySet()) {
            used.add(e.getKey());
            g.writeNumberField(e.getKey(), e.getValue());
        }
        g.writeEndObject();

        g.writeArrayFieldStart("modules");
        for (MutationModuleEvidence module : mutation) {
            writeMutationModule(g, module, used, testIds);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeMutationModule(JsonGenerator g, MutationModuleEvidence module, Set<String> used,
                                             Map<String, Integer> testIds)
        throws IOException {
        Map<String, List<MutatedMethod>> byClass = new LinkedHashMap<>();
        for (MutatedMethod method : module.methods()) {
            byClass.computeIfAbsent(method.className(), k -> new ArrayList<>()).add(method);
        }
        List<String> classNames = new ArrayList<>(byClass.keySet());
        Collections.sort(classNames);

        g.writeStartObject();
        g.writeStringField("moduleId", module.moduleId());
        g.writeArrayFieldStart("classes");
        for (String className : classNames) {
            List<MutatedMethod> methods = byClass.get(className);
            Map<String, Long> classCounts = new LinkedHashMap<>();
            for (MutatedMethod method : methods) {
                for (Mutant m : method.mutants()) {
                    classCounts.merge(m.status(), 1L, Long::sum);
                }
            }
            g.writeStartObject();
            g.writeStringField("className", className);
            g.writeObjectFieldStart("countsByStatus");
            for (Map.Entry<String, Long> e : classCounts.entrySet()) {
                used.add(e.getKey());
                g.writeNumberField(e.getKey(), e.getValue());
            }
            g.writeEndObject();
            g.writeArrayFieldStart("methods");
            for (MutatedMethod method : methods) {
                writeMutatedMethod(g, method, used, testIds);
            }
            g.writeEndArray();
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeMutatedMethod(JsonGenerator g, MutatedMethod method, Set<String> used,
                                            Map<String, Integer> testIds) throws IOException {
        g.writeStartObject();
        g.writeStringField("methodName", method.methodName());
        g.writeStringField("signatureShort", method.methodName() + simplifyParams(method.methodDescription()));
        g.writeStringField("signatureFull", method.methodName() + method.methodDescription());
        g.writeNumberField("firstLine", method.firstLine());
        g.writeNumberField("lastLine", method.lastLine());
        g.writeArrayFieldStart("mutants");
        List<Mutant> sorted = method.mutants().stream().sorted(Comparator.comparingInt(Mutant::line)).toList();
        for (Mutant m : sorted) {
            used.add(m.status());
            g.writeStartObject();
            g.writeStringField("mutator", m.mutator());
            g.writeStringField("mutatorShort", TestLabels.shortMutator(m.mutator()));
            g.writeNumberField("line", m.line());
            g.writeStringField("status", m.status());
            g.writeArrayFieldStart("killingTests");
            for (String t : m.killingTests()) {
                g.writeNumber(testIds.get(t));
            }
            g.writeEndArray();
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    /**
     * A JVM method descriptor's parameter list (e.g. {@code
     * (Lcom/google/gson/stream/JsonReader;)Ljava/lang/Object;}) rendered as
     * simple type names ({@code (JsonReader)}) - the fully-qualified
     * descriptor is never dropped, only accompanied: {@code
     * signatureFull} carries it verbatim for anyone who needs the exact
     * bytecode signature (an overload with an identically-named erased
     * parameter, for instance).
     */
    static String simplifyParams(String descriptor) {
        int open = descriptor.indexOf('(');
        int close = descriptor.indexOf(')', open + 1);
        if (open < 0 || close < 0) {
            return descriptor;
        }
        String params = descriptor.substring(open + 1, close);
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < params.length()) {
            ParsedParam parsed = parseOneParam(params, i);
            if (parsed == null) {
                break;
            }
            parts.add(parsed.rendered());
            i = parsed.nextIndex();
        }
        return "(" + String.join(", ", parts) + ")";
    }

    /** One descriptor parameter's simple-name rendering plus the index just past it, or {@code null} on a truncated/malformed descriptor (SonarQube java:S3776 - the loop body in {@link #simplifyParams} above). */
    private record ParsedParam(String rendered, int nextIndex) {
    }

    private static ParsedParam parseOneParam(String params, int start) {
        int i = start;
        int arrayDims = 0;
        while (i < params.length() && params.charAt(i) == '[') {
            arrayDims++;
            i++;
        }
        if (i >= params.length()) {
            return null;
        }
        char c = params.charAt(i);
        String type;
        if (c == 'L') {
            int semi = params.indexOf(';', i);
            if (semi < 0) {
                return null;
            }
            String full = params.substring(i + 1, semi);
            int cut = Math.max(full.lastIndexOf('/'), full.lastIndexOf('$'));
            type = cut >= 0 ? full.substring(cut + 1) : full;
            i = semi + 1;
        } else {
            type = switch (c) {
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'Z' -> "boolean";
                default -> String.valueOf(c);
            };
            i++;
        }
        return new ParsedParam(type + "[]".repeat(arrayDims), i);
    }

    // ---- File coverage: flat, source-root-relative file list (D-81) ----
    //
    // D-80 tried a nested, path-compressed folder tree here; D-81 replaced it with a flat list
    // once the report moved to a fixed dashboard (no more expand-a-folder browsing UI - the
    // "Files" card sorts/filters this flat list by risk or groups it by immediate package
    // client-side). displayPath strips each entry's own module-root+source-root prefix (from
    // that module's declared sourceRoots, never a guess) so "gson/src/main/java/com/google/gson/
    // internal/bind/TreeTypeAdapter.java" reads as "com/google/gson/internal/bind/TreeTypeAdapter.java"
    // - the full repo-relative path is still carried as `path` for anyone who needs it verbatim.

    private static void writeFileCoverage(JsonGenerator g, FileCoverageBlock block, List<ModuleInput> modules)
        throws IOException {
        g.writeObjectFieldStart("fileCoverage");
        g.writeNumberField("totalFiles", block.files().size());
        g.writeArrayFieldStart("excluded");
        for (String p : block.excluded()) {
            g.writeString(p);
        }
        g.writeEndArray();

        List<FileCoverageEntry> sorted = block.files().stream()
            .sorted(Comparator.comparing(FileCoverageEntry::path)).toList();
        g.writeArrayFieldStart("files");
        for (FileCoverageEntry entry : sorted) {
            writeFileCoverageEntry(g, entry, modules);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeFileCoverageEntry(JsonGenerator g, FileCoverageEntry entry, List<ModuleInput> modules)
        throws IOException {
        Metric metric = entry.metrics().sonarCompatible();
        String display = sourceRelativePath(entry, modules);
        g.writeStartObject();
        g.writeStringField(FIELD_MODULE, entry.module());
        g.writeStringField("path", entry.path());
        g.writeStringField("displayPath", display);
        g.writeStringField("packagePath", packageOf(display));
        g.writeStringField("fileName", fileNameOnly(display));
        BigDecimal percent = metric.percent();
        g.writeStringField("pctText", formatPercent(percent));
        if (percent == null) {
            g.writeNullField("pct");
        } else {
            g.writeNumberField("pct", percent);
        }
        g.writeNumberField("numerator", metric.numerator());
        g.writeNumberField("denominator", metric.denominator());
        g.writeEndObject();
    }

    /** Strips {@code entry}'s own module-root+source-root prefix using that module's declared {@code sourceRoots} - falls back to the full repo-relative path when no declared root matches (never guesses at one). */
    private static String sourceRelativePath(FileCoverageEntry entry, List<ModuleInput> modules) {
        for (ModuleInput module : modules) {
            if (!module.id().equals(entry.module())) {
                continue;
            }
            String rootPrefix = ".".equals(module.root()) ? "" : module.root() + "/";
            for (String sourceRoot : module.sourceRoots()) {
                String prefix = rootPrefix + sourceRoot + "/";
                if (entry.path().startsWith(prefix)) {
                    return entry.path().substring(prefix.length());
                }
            }
        }
        return entry.path();
    }

    private static String packageOf(String displayPath) {
        int idx = displayPath.lastIndexOf('/');
        return idx < 0 ? "" : displayPath.substring(0, idx);
    }

    private static String fileNameOnly(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    // ---- Labels: only the codes this exact report used (hard rule 5: id always kept alongside the name) ----

    private static void writeLabels(JsonGenerator g, Set<String> usedCodes) throws IOException {
        g.writeObjectFieldStart("labels");
        for (String code : usedCodes) {
            ReportLabels.Label label = ReportLabels.lookup(code);
            if (label == null) {
                continue;
            }
            g.writeObjectFieldStart(code);
            g.writeStringField("name", label.name());
            g.writeStringField("description", label.description());
            g.writeEndObject();
        }
        g.writeEndObject();
    }
}
