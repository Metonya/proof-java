package dev.coverdict.analysis.report;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

import dev.coverdict.analysis.metrics.Metric;
import dev.coverdict.analysis.metrics.MetricSet;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.LineRange;
import dev.coverdict.analysis.mutation.Mutant;
import dev.coverdict.analysis.mutation.MutatedMethod;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;
import dev.coverdict.analysis.pertest.PerTestEntry;
import dev.coverdict.analysis.pertest.PerTestModuleEvidence;
import dev.coverdict.analysis.vcs.VcsIdentity;

/**
 * D-80: the HTML report's presentation data, as one JSON object
 * {@link HtmlRenderer} embeds verbatim in a {@code <script
 * type="application/json">} for the report's own client-side script to
 * read. Separate from {@link VerdictJsonWriter} on purpose - that one
 * writes the schema-contract document (hard rule 7); this one writes
 * display-shaped data (Turkish-formatted numbers, a friendly-name lookup
 * for only the codes this exact report uses, a path-compressed file tree)
 * that has no business in the verdict JSON schema.
 *
 * <p>Reads only what {@code doc} already carries and never computes a new
 * metric: every numerator/denominator pair below is copied or summed from
 * already-computed pairs (hard rule 4), and every percentage is
 * recalculated with the exact same scale-1/HALF_UP rule {@link
 * dev.coverdict.analysis.metrics.Metric#of} uses, so a folder's rolled-up
 * percentage in the file tree matches what {@code Metric.of} would have
 * produced from the same numerator/denominator.
 */
final class ReportDataWriter {

    private static final Locale TR = new Locale("tr", "TR");
    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", TR);
    private static final JsonFactory FACTORY = new JsonFactory();

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
                writeMutation(g, doc.mutation(), used);
            }
            if (doc.fileCoverage() != null) {
                writeFileCoverage(g, doc.fileCoverage());
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
        g.writeStringField("statusLabel", doc.complete() ? "tamamlandı" : "eksik");
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
            case "no-vcs" -> "fark hesaplanmadı (no-vcs)";
            case "working-tree" -> "commit bekleyen değişiklikler (uncommitted)";
            case "base-ref" -> "belirtilen referansla fark (base-ref)";
            default -> diffMode;
        };
    }

    private static String findingsScopeLabel(String findingsScope) {
        return "changed".equals(findingsScope) ? "sadece değişen test dosyaları" : "tüm test dosyaları";
    }

    /** The full v0.1 rule catalog, sorted, always written in full - a rule with zero findings in this run still gets a soft "0" chip in the report (D-80: "0 bir bilgidir"), which needs to know the rule exists at all, not just the ones that fired. */
    private static void writeRuleIds(JsonGenerator g, Set<String> used) throws IOException {
        g.writeArrayFieldStart("ruleIds");
        List<String> sorted = new ArrayList<>(dev.coverdict.analysis.model.RuleIds.ALL);
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
        return percent == null ? "n/a" : percent.toString().replace('.', ',') + "%";
    }

    private static String formatInt(int n) {
        return String.format(TR, "%,d", n);
    }

    // ---- Changed files (D-80 fix: section is always present, even when empty - hard rule 3a) ----

    private static void writeChangedFiles(JsonGenerator g, VerdictDocument doc, Set<String> used) throws IOException {
        g.writeArrayFieldStart("changedFiles");
        for (ChangedFile file : doc.changedFiles()) {
            used.add(file.classification().schemaValue());
            g.writeStartObject();
            g.writeStringField("module", file.module());
            g.writeStringField("path", file.path());
            g.writeStringField("classification", file.classification().schemaValue());
            g.writeStringField("search",
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
            g.writeStringField("search", joinNonBlank(f.rule(), f.severity().name(), f.confidence().name(), f.path(),
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
                g.writeStringField("module", r.module());
            }
            if (r.count() != null) {
                g.writeNumberField("count", r.count());
            }
            g.writeStringField("search", joinNonBlank(r.code(), r.message(), r.path()).toLowerCase(Locale.ROOT));
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

    // ---- Mutation (L3): per-status counts (no "diğer" catch-all bucket - D-80 fix), simplified signatures ----

    private static void writeMutation(JsonGenerator g, List<MutationModuleEvidence> mutation, Set<String> used)
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
            writeMutationModule(g, module, used);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeMutationModule(JsonGenerator g, MutationModuleEvidence module, Set<String> used)
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
                writeMutatedMethod(g, method, used);
            }
            g.writeEndArray();
            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void writeMutatedMethod(JsonGenerator g, MutatedMethod method, Set<String> used) throws IOException {
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
            g.writeNumberField("line", m.line());
            g.writeStringField("status", m.status());
            g.writeArrayFieldStart("killingTests");
            for (String t : m.killingTests()) {
                g.writeString(t);
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
            int arrayDims = 0;
            while (i < params.length() && params.charAt(i) == '[') {
                arrayDims++;
                i++;
            }
            if (i >= params.length()) {
                break;
            }
            char c = params.charAt(i);
            String type;
            if (c == 'L') {
                int semi = params.indexOf(';', i);
                if (semi < 0) {
                    break;
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
            parts.add(type + "[]".repeat(arrayDims));
        }
        return "(" + String.join(", ", parts) + ")";
    }

    // ---- File coverage: path-compressed tree (D-80 fix for the 8-click single-child chain, #1) ----

    private static final class TreeNode {
        final Map<String, TreeNode> children = new LinkedHashMap<>();
        final List<FileCoverageEntry> files = new ArrayList<>();
    }

    private record Agg(int numerator, int denominator) {
        static final Agg ZERO = new Agg(0, 0);

        Agg plus(Agg other) {
            return new Agg(numerator + other.numerator, denominator + other.denominator);
        }

        BigDecimal percent() {
            if (denominator == 0) {
                return null;
            }
            return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
        }

        String percentText() {
            BigDecimal p = percent();
            return p == null ? "n/a" : p.toString().replace('.', ',') + "%";
        }
    }

    private static void writeFileCoverage(JsonGenerator g, FileCoverageBlock block) throws IOException {
        g.writeObjectFieldStart("fileCoverage");
        g.writeNumberField("totalFiles", block.files().size());
        g.writeArrayFieldStart("excluded");
        for (String p : block.excluded()) {
            g.writeString(p);
        }
        g.writeEndArray();

        Map<String, TreeNode> byModule = new LinkedHashMap<>();
        for (FileCoverageEntry entry : block.files()) {
            TreeNode moduleRoot = byModule.computeIfAbsent(entry.module(), k -> new TreeNode());
            insertIntoTree(moduleRoot, entry);
        }
        List<String> moduleIds = new ArrayList<>(byModule.keySet());
        Collections.sort(moduleIds);
        g.writeArrayFieldStart("tree");
        for (String moduleId : moduleIds) {
            writeTreeNode(g, new ArrayList<>(), byModule.get(moduleId), moduleId);
        }
        g.writeEndArray();
        g.writeEndObject();
    }

    private static void insertIntoTree(TreeNode root, FileCoverageEntry entry) {
        String[] segments = entry.path().split("/");
        TreeNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            current = current.children.computeIfAbsent(segments[i], k -> new TreeNode());
        }
        current.files.add(entry);
    }

    /**
     * Writes {@code node} as one row, first compressing forward through
     * every ancestor that contributes no branching (a folder with exactly
     * one child folder and no files merges into its child's name; a folder
     * with exactly one file and no subfolders merges into a single file
     * row) - the client never has to click through a folder that had no
     * real choice to offer. {@code fallbackName} is used only when
     * compression never advances at all (the root already branches), so a
     * module whose paths don't start with its own id still gets a label.
     * Returns the aggregate numerator/denominator sum so a parent can add
     * an already-computed pair rather than deriving a new metric (hard
     * rule 4).
     */
    private static Agg writeTreeNode(JsonGenerator g, List<String> nameParts, TreeNode node, String fallbackName)
        throws IOException {
        while (node.children.size() + node.files.size() == 1) {
            if (!node.files.isEmpty()) {
                FileCoverageEntry only = node.files.get(0);
                List<String> leafParts = new ArrayList<>(nameParts);
                leafParts.add(fileNameOnly(only.path()));
                return writeFileLeaf(g, joinPath(leafParts, fallbackName), only);
            }
            Map.Entry<String, TreeNode> onlyChild = node.children.entrySet().iterator().next();
            nameParts = new ArrayList<>(nameParts);
            nameParts.add(onlyChild.getKey());
            node = onlyChild.getValue();
        }

        List<String> childNames = new ArrayList<>(node.children.keySet());
        Collections.sort(childNames);
        List<FileCoverageEntry> sortedFiles = node.files.stream()
            .sorted(Comparator.comparing(FileCoverageEntry::path)).toList();

        g.writeStartObject();
        g.writeStringField("name", joinPath(nameParts, fallbackName));
        g.writeBooleanField("isFile", false);
        g.writeArrayFieldStart("children");
        Agg agg = Agg.ZERO;
        for (String childName : childNames) {
            agg = agg.plus(writeTreeNode(g, new ArrayList<>(List.of(childName)), node.children.get(childName), childName));
        }
        for (FileCoverageEntry entry : sortedFiles) {
            agg = agg.plus(writeFileLeaf(g, fileNameOnly(entry.path()), entry));
        }
        g.writeEndArray();
        writeAggFields(g, agg);
        g.writeEndObject();
        return agg;
    }

    private static String joinPath(List<String> parts, String fallbackWhenEmpty) {
        return parts.isEmpty() ? fallbackWhenEmpty : String.join("/", parts);
    }

    private static Agg writeFileLeaf(JsonGenerator g, String name, FileCoverageEntry entry) throws IOException {
        Metric metric = entry.metrics().sonarCompatible();
        Agg agg = new Agg(metric.numerator(), metric.denominator());
        g.writeStartObject();
        g.writeStringField("name", name);
        g.writeBooleanField("isFile", true);
        g.writeStringField("path", entry.path());
        writeAggFields(g, agg);
        g.writeEndObject();
        return agg;
    }

    private static void writeAggFields(JsonGenerator g, Agg agg) throws IOException {
        g.writeStringField("pctText", agg.percentText());
        BigDecimal percent = agg.percent();
        if (percent == null) {
            g.writeNullField("pct");
        } else {
            g.writeNumberField("pct", percent);
        }
        g.writeNumberField("numerator", agg.numerator());
        g.writeNumberField("denominator", agg.denominator());
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
