package dev.coverdict.analysis.report;

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
import java.util.stream.Collectors;

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
 * Human-readable, standalone HTML rendering of a {@link VerdictDocument}
 * (hard rule 7: same document, now three readers - {@link VerdictJsonWriter}
 * for machines, {@link TextRenderer} for a terminal, this for a browser).
 * Reads only what {@code doc} already carries - the mutation/file-coverage
 * tree sections below sum already-computed per-file/per-mutant numbers
 * verbatim (plain addition of numerator/denominator pairs that already
 * share one name per metric mode), never inventing a new metric definition
 * (hard rule 4, same rule {@link VerdictDocument}'s own javadoc states for
 * {@code fileCoverage}).
 *
 * <p>Every input-derived string (a path, a message, a rule id, a PIT mutant
 * status - all ultimately sourced from parsed repo content or a subprocess'
 * own output) is untrusted per SECURITY-POLICY.md's own threat model and is
 * passed through {@link #esc(String)} before landing in the HTML body -
 * control characters get {@link TextRenderer}'s own backslash-u-escape
 * treatment, and {@code &}/{@code <}/{@code >}/{@code "}/{@code '} get
 * HTML-entity escaped, so a crafted path or message can never break out of
 * its text node or a {@code data-*} attribute (D-76: every {@code data-*}
 * value below goes through the same {@link #esc(String)} as visible text).
 *
 * <p>D-76 adds one small, entirely static {@code <script>} ({@link #SCRIPT})
 * powering client-side filtering for the mutation and file-coverage
 * sections - it is fixed source text with no template interpolation ever,
 * and it only reads already-escaped {@code data-*} attributes and toggles
 * {@code style.display}/{@code open}; it never uses {@code innerHTML} or
 * {@code eval} on anything request-derived, so the no-injection guarantee
 * above still holds with it in place.
 *
 * <p>Self-contained by design: no external stylesheet, font, or script
 * reference - the CLI makes zero network calls (SECURITY-POLICY.md #5) and
 * a report opened offline must render identically to one opened online. The
 * declared font stacks fall back to system fonts when IBM Plex isn't
 * installed locally.
 *
 * <p>D-77: every {@code <table>} is wrapped in a scrollable {@code
 * .table-wrap} box ({@link #openTable}/{@link #closeTable}) so one
 * abnormally long value scrolls inside its own table rather than forcing
 * the whole page to scroll horizontally. The header also carries a manual
 * light/dark toggle button on top of the existing {@code
 * prefers-color-scheme} default - see {@code :root[data-theme]} in {@link
 * #CSS} and the {@code coverdictSetTheme}/{@code coverdictToggleTheme}
 * functions in {@link #SCRIPT}.
 */
public final class HtmlRenderer {

    private HtmlRenderer() {
    }

    public static String render(VerdictDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<title>coverdict report</title>\n");
        sb.append("<style>\n").append(CSS).append("\n</style>\n");
        sb.append("</head>\n<body>\n<main>\n");

        renderHeader(sb, doc);
        renderCoverage(sb, doc);
        renderChangedFiles(sb, doc);
        renderFindings(sb, doc);
        renderReasons(sb, "Uyarılar", doc.warnings());
        renderReasons(sb, "Eksik nedenler", doc.incompleteReasons());
        if (doc.perTest() != null) {
            renderPerTest(sb, doc.perTest());
        }
        if (doc.mutation() != null) {
            renderMutation(sb, doc.mutation());
        }
        if (doc.fileCoverage() != null) {
            renderFileCoverage(sb, doc.fileCoverage());
        }

        sb.append("<footer><p>coverdict ").append(esc(doc.toolVersion()))
            .append(" &middot; schema ").append(esc(doc.schemaVersion())).append("</p></footer>\n");
        sb.append("<script>\n").append(SCRIPT).append("\n</script>\n");
        sb.append("</main>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", new Locale("tr", "TR"));

    /**
     * D-79: what/when/which-settings, replacing the earlier raw git-identity
     * dump. {@code doc} itself carries no "when the evidence was collected"
     * timestamp by design (the JSON stays byte-deterministic, D-01/D-75) -
     * {@code Instant.now()} here is honestly the render time, not the scan
     * time, and is labeled as such rather than implying otherwise.
     */
    private static void renderHeader(StringBuilder sb, VerdictDocument doc) {
        sb.append("<header>\n");
        sb.append("<button type=\"button\" id=\"theme-toggle\" class=\"theme-toggle\" ")
            .append("onclick=\"coverdictToggleTheme()\" aria-label=\"Açık/koyu tema değiştir\">&#127769; Koyu Mod</button>\n");
        sb.append("<h1>coverdict raporu</h1>\n");
        sb.append("<p class=\"generated-at\">Rapor oluşturulma zamanı: ")
            .append(esc(GENERATED_AT_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault())))).append("</p>\n");

        String statusClass = doc.complete() ? "status-complete" : "status-incomplete";
        String statusLabel = doc.complete() ? "tamamlandı" : "eksik";
        sb.append("<p class=\"status ").append(statusClass).append("\">").append(statusLabel).append("</p>\n");

        sb.append("<dl class=\"identity\">\n");
        dt(sb, "Modül(ler)", moduleSummary(doc.modules()));
        dt(sb, "Fark modu", diffModeLabel(doc.diffMode()));
        VcsIdentity identity = doc.identity();
        if (identity != null) {
            if (identity.baseRef() != null) {
                dt(sb, "Karşılaştırma referansı", identity.baseRef());
            }
            renderCommitRows(sb, identity);
            dt(sb, "Kaydedilmemiş değişiklik", identity.dirty() ? "var" : "yok");
        }
        dt(sb, "Dil seviyesi", String.valueOf(doc.languageLevel()));
        dt(sb, "Encoding", doc.encoding());
        dt(sb, "Bulgu kapsamı", findingsScopeLabel(doc.findingsScope()));
        if (!doc.exclusions().isEmpty()) {
            dt(sb, "Hariç tutulan desenler", String.join(", ", doc.exclusions()));
        }
        sb.append("</dl>\n");
        sb.append("</header>\n");
    }

    /** Base/merge-base/HEAD collapse into one row when they're all the same commit (the common case outside base-ref mode) - three identical 40-char hashes stacked on top of each other said nothing a reader could use. */
    private static void renderCommitRows(StringBuilder sb, VcsIdentity identity) {
        Set<String> distinct = new LinkedHashSet<>();
        if (identity.base() != null) {
            distinct.add(identity.base());
        }
        if (identity.mergeBase() != null) {
            distinct.add(identity.mergeBase());
        }
        distinct.add(identity.head());
        if (distinct.size() == 1) {
            dtCode(sb, "Commit", distinct.iterator().next());
            return;
        }
        if (identity.base() != null) {
            dtCode(sb, "Base commit", identity.base());
        }
        if (identity.mergeBase() != null) {
            dtCode(sb, "Merge-base", identity.mergeBase());
        }
        dtCode(sb, "HEAD", identity.head());
    }

    private static String moduleSummary(List<ModuleInput> modules) {
        if (modules.isEmpty()) {
            return "—";
        }
        return modules.stream().map(m -> m.id() + " (" + m.root() + ")").collect(Collectors.joining(", "));
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

    private static void dt(StringBuilder sb, String key, String value) {
        sb.append("<dt>").append(esc(key)).append("</dt><dd>").append(esc(value)).append("</dd>\n");
    }

    /** A full 40-char SHA is shown short (git's own convention) with the full value in a hover tooltip - never truncated data with no way to see the rest. */
    private static void dtCode(StringBuilder sb, String key, String sha) {
        String shortSha = sha.length() > 7 ? sha.substring(0, 7) : sha;
        sb.append("<dt>").append(esc(key)).append("</dt><dd><code title=\"").append(esc(sha)).append("\">")
            .append(esc(shortSha)).append("</code></dd>\n");
    }

    /**
     * D-79: every top-level section is a collapsible {@code <details>} (the
     * same "kapatıp açabilmeliyim" the mutation/file-coverage sub-sections
     * already had) - open by default, so nothing looks different at first
     * render, but a section the reader doesn't care about (typically
     * Uyarılar) can be closed like any other. {@code titleHtml} is
     * caller-built and already {@link #esc(String)}-safe wherever it embeds
     * dynamic text (a count, a scope name) - never raw input concatenated
     * in directly.
     */
    private static void openSection(StringBuilder sb, String id, String titleHtml) {
        sb.append("<details open class=\"report-section\"");
        if (id != null) {
            sb.append(" id=\"").append(id).append('"');
        }
        sb.append(">\n<summary><h2>").append(titleHtml).append("</h2></summary>\n<div class=\"report-section-body\">\n");
    }

    private static void closeSection(StringBuilder sb) {
        sb.append("</div>\n</details>\n");
    }

    /**
     * Every {@code <table>} opens inside a {@code .table-wrap} (CSS: {@code
     * overflow-x: auto}) - a real, unbroken long value (a deep package path,
     * a long test method name, a joined killing-tests list) must scroll
     * inside its own table, never force the whole page to scroll
     * horizontally. {@code cssClass} is always one of this file's own fixed
     * literals, never input-derived - no escaping needed for it specifically.
     *
     * <p>D-79: {@code filterPlaceholder}, when given, adds a text filter
     * input above the table wired to the generic {@code coverdictFilterRows}
     * (matches against each {@code <tr data-search="...">}'s pre-built,
     * already-escaped search text) - every table-shaped section gets one,
     * not just mutation/file-coverage.
     */
    private static void openTable(StringBuilder sb, String cssClass, String filterPlaceholder) {
        sb.append("<div class=\"table-wrap");
        if (filterPlaceholder != null) {
            sb.append(" filterable");
        }
        sb.append("\">\n");
        if (filterPlaceholder != null) {
            sb.append("<input type=\"text\" class=\"filter-input\" placeholder=\"").append(esc(filterPlaceholder))
                .append("\" oninput=\"coverdictFilterRows(this)\">\n");
        }
        sb.append("<table").append(cssClass != null ? " class=\"" + cssClass + "\"" : "").append(">\n");
    }

    private static void closeTable(StringBuilder sb) {
        sb.append("</table></div>\n");
    }

    private static void renderCoverage(StringBuilder sb, VerdictDocument doc) {
        openSection(sb, null, "Kapsama");
        sb.append("<h3>Genel</h3>\n");
        renderMetricSetTable(sb, doc.overallMetrics());
        sb.append("<h3>Yeni kod</h3>\n");
        if (doc.newCode().metrics() != null) {
            renderMetricSetTable(sb, doc.newCode().metrics());
        } else {
            sb.append("<p class=\"unavailable\">").append(esc(doc.newCode().unavailableStatus())).append("</p>\n");
        }
        closeSection(sb);
    }

    private static void renderMetricSetTable(StringBuilder sb, MetricSet metrics) {
        openTable(sb, "metrics", null);
        sb.append("<thead><tr><th>mod</th><th>%</th><th>oran</th></tr></thead>\n<tbody>\n");
        metricRow(sb, "jacoco-line", metrics.jacocoLine());
        metricRow(sb, "strict-line", metrics.strictLine());
        metricRow(sb, "sonar-compatible", metrics.sonarCompatible());
        sb.append("</tbody>\n");
        closeTable(sb);
    }

    private static void metricRow(StringBuilder sb, String name, Metric metric) {
        BigDecimal percent = metric.percent();
        String percentText = percent == null ? "n/a" : percent + "%";
        sb.append("<tr><td>").append(esc(name)).append("</td><td>").append(esc(percentText))
            .append("</td><td>").append(metric.numerator()).append('/').append(metric.denominator())
            .append(" (").append(esc(metric.numeratorName())).append('/').append(esc(metric.denominatorName()))
            .append(")</td></tr>\n");
    }

    private static void renderChangedFiles(StringBuilder sb, VerdictDocument doc) {
        List<ChangedFile> files = doc.changedFiles();
        if (files.isEmpty()) {
            return;
        }
        openSection(sb, null, "Değişen dosyalar (" + files.size() + ")");
        openTable(sb, null, "modül, yol veya sınıflandırmaya göre filtrele");
        sb.append("<thead><tr><th>modül</th><th>yol</th><th>sınıflandırma</th><th>yeni satır</th><th>kapsanan</th><th>kapsanmayan aralıklar</th></tr></thead>\n<tbody>\n");
        for (ChangedFile file : files) {
            String searchBlob = joinNonBlank(file.module(), file.path(), file.classification().schemaValue())
                .toLowerCase(Locale.ROOT);
            sb.append("<tr data-search=\"").append(esc(searchBlob)).append("\"><td>").append(esc(file.module()))
                .append("</td><td><code>").append(esc(file.path()))
                .append("</code></td><td>").append(esc(file.classification().schemaValue())).append("</td>");
            if (file.newLines() != null) {
                sb.append("<td>").append(file.newLines()).append("</td><td>").append(file.coveredNewLines())
                    .append("</td><td>").append(esc(formatRanges(file.uncoveredNewRanges()))).append("</td>");
            } else {
                sb.append("<td>-</td><td>-</td><td>-</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n");
        closeTable(sb);
        closeSection(sb);
    }

    /** Joins non-null, non-blank parts with a single space - unlike {@code String.join}, an absent optional field (a null path, a null module) never leaves a stray double space or trailing space in the built search text. */
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

    private static void renderFindings(StringBuilder sb, VerdictDocument doc) {
        List<Finding> findings = doc.findings();
        String title = "Bulgular (" + findings.size() + ", kapsam: " + esc(doc.findingsScope()) + ")";
        openSection(sb, null, title);
        if (findings.isEmpty()) {
            sb.append("<p class=\"empty\">Bulgu yok.</p>\n");
            closeSection(sb);
            return;
        }
        List<Finding> sorted = findings.stream()
            .sorted(Comparator.comparing(Finding::path).thenComparingInt(Finding::startLine)
                .thenComparing(Finding::rule))
            .toList();
        openTable(sb, null, "kural, önem, dosya, test veya mesaja göre filtrele");
        sb.append("<thead><tr><th>kural</th><th>önem</th><th>güven</th><th>konum</th><th>test</th><th>mesaj</th><th>öneri</th></tr></thead>\n<tbody>\n");
        for (Finding f : sorted) {
            String anchorMethod = f.testMethod() != null ? f.testMethod() : f.productionMethod();
            String searchBlob = joinNonBlank(f.rule(), f.severity().name(), f.confidence().name(), f.path(),
                anchorMethod, f.message(), f.suggestedAction()).toLowerCase(Locale.ROOT);
            sb.append("<tr data-search=\"").append(esc(searchBlob)).append("\"><td>").append(esc(f.rule()))
                .append("</td><td>").append(esc(f.severity().name()))
                .append("</td><td>").append(esc(f.confidence().name())).append("</td><td><code>")
                .append(esc(f.path())).append(':').append(f.startLine());
            if (f.endLine() != f.startLine()) {
                sb.append('-').append(f.endLine());
            }
            sb.append("</code></td><td>").append(esc(anchorMethod))
                .append("</td><td>").append(esc(f.message())).append("</td><td>").append(esc(f.suggestedAction()))
                .append("</td></tr>\n");
        }
        sb.append("</tbody>\n");
        closeTable(sb);
        closeSection(sb);
    }

    private static void renderReasons(StringBuilder sb, String title, List<AnalysisReason> reasons) {
        if (reasons.isEmpty()) {
            return;
        }
        openSection(sb, null, esc(title) + " (" + reasons.size() + ")");
        sb.append("<div class=\"filterable\">\n");
        sb.append("<input type=\"text\" class=\"filter-input\" placeholder=\"koda veya mesaja göre filtrele\" oninput=\"coverdictFilterRows(this)\">\n");
        sb.append("<ul>\n");
        for (AnalysisReason r : reasons) {
            String searchBlob = joinNonBlank(r.code(), r.message(), r.path()).toLowerCase(Locale.ROOT);
            sb.append("<li data-search=\"").append(esc(searchBlob)).append("\"><code>").append(esc(r.code()))
                .append("</code>: ").append(esc(r.message()));
            if (r.path() != null) {
                sb.append(" <code>").append(esc(r.path())).append("</code>");
            }
            if (r.count() != null) {
                sb.append(" (").append(r.count()).append(')');
            }
            sb.append("</li>\n");
        }
        sb.append("</ul>\n</div>\n");
        closeSection(sb);
    }

    private static void renderPerTest(StringBuilder sb, List<PerTestModuleEvidence> perTest) {
        openSection(sb, null, "Test bazlı kanıt (L2)");
        openTable(sb, null, "modüle göre filtrele");
        sb.append("<thead><tr><th>modül</th><th>metot sayısı</th><th>ambient</th></tr></thead>\n<tbody>\n");
        for (PerTestModuleEvidence module : perTest) {
            sb.append("<tr data-search=\"").append(esc(module.moduleId().toLowerCase(Locale.ROOT))).append("\"><td>")
                .append(esc(module.moduleId())).append("</td><td>")
                .append(countLines(module.entries())).append("</td><td>")
                .append(countLines(module.ambient())).append("</td></tr>\n");
        }
        sb.append("</tbody>\n");
        closeTable(sb);
        closeSection(sb);
    }

    private static int countLines(List<PerTestEntry> entries) {
        return entries.stream().mapToInt(e -> e.lines().size()).sum();
    }

    // ---- Mutation (L3): module summary + per-class collapsible, filterable detail ----

    private record MutantCounts(long killed, long survived, long other) {
        static MutantCounts of(List<Mutant> mutants) {
            long killed = mutants.stream().filter(m -> "KILLED".equals(m.status())).count();
            long survived = mutants.stream().filter(m -> "SURVIVED".equals(m.status())).count();
            return new MutantCounts(killed, survived, mutants.size() - killed - survived);
        }

        MutantCounts plus(MutantCounts other) {
            return new MutantCounts(killed + other.killed, survived + other.survived, this.other + other.other);
        }
    }

    private static void renderMutation(StringBuilder sb, List<MutationModuleEvidence> mutation) {
        openSection(sb, "mutation", "Mutasyon kanıtı (L3)");
        sb.append("<div class=\"filter-bar\">\n")
            .append("<input type=\"text\" id=\"mutation-filter\" class=\"filter-input\" ")
            .append("placeholder=\"sınıf, metot, mutator veya öldüren teste göre filtrele\" oninput=\"coverdictFilterMutation()\">\n")
            .append("<label><input type=\"checkbox\" id=\"mutation-survived-only\" onchange=\"coverdictFilterMutation()\"> sadece SURVIVED</label>\n")
            .append("</div>\n");

        MutantCounts total = new MutantCounts(0, 0, 0);
        for (MutationModuleEvidence module : mutation) {
            for (MutatedMethod method : module.methods()) {
                total = total.plus(MutantCounts.of(method.mutants()));
            }
        }
        sb.append("<p class=\"mutation-total\">Toplam: <strong>").append(total.killed()).append("</strong> KILLED, <strong>")
            .append(total.survived()).append("</strong> SURVIVED, <strong>").append(total.other()).append("</strong> diğer</p>\n");

        for (MutationModuleEvidence module : mutation) {
            renderMutationModule(sb, module);
        }
        closeSection(sb);
    }

    private static void renderMutationModule(StringBuilder sb, MutationModuleEvidence module) {
        Map<String, List<MutatedMethod>> byClass = new LinkedHashMap<>();
        for (MutatedMethod method : module.methods()) {
            byClass.computeIfAbsent(method.className(), k -> new ArrayList<>()).add(method);
        }
        List<String> classNames = new ArrayList<>(byClass.keySet());
        Collections.sort(classNames);

        sb.append("<h3>Modül: <code>").append(esc(module.moduleId())).append("</code></h3>\n");
        for (String className : classNames) {
            List<MutatedMethod> methods = byClass.get(className);
            MutantCounts classCounts = new MutantCounts(0, 0, 0);
            for (MutatedMethod method : methods) {
                classCounts = classCounts.plus(MutantCounts.of(method.mutants()));
            }
            sb.append("<details class=\"mutation-class\">\n<summary><code>").append(esc(className))
                .append("</code> &middot; ").append(classCounts.killed()).append(" killed &middot; ")
                .append(classCounts.survived()).append(" survived &middot; ").append(classCounts.other())
                .append(" diğer</summary>\n");
            for (MutatedMethod method : methods) {
                renderMutatedMethod(sb, className, method);
            }
            sb.append("</details>\n");
        }
    }

    private static void renderMutatedMethod(StringBuilder sb, String className, MutatedMethod method) {
        sb.append("<h4><code>").append(esc(className)).append('#').append(esc(method.methodName()))
            .append(esc(method.methodDescription())).append("</code> (satır ").append(method.firstLine())
            .append('-').append(method.lastLine()).append(")</h4>\n");
        openTable(sb, "mutants", null);
        sb.append("<thead><tr><th>mutator</th><th>satır</th><th>durum</th><th>öldüren testler</th></tr></thead>\n<tbody>\n");
        List<Mutant> sortedMutants = method.mutants().stream().sorted(Comparator.comparingInt(Mutant::line)).toList();
        for (Mutant m : sortedMutants) {
            String statusToken = m.status().replaceAll("[^A-Za-z0-9_-]", "_");
            String killingTests = String.join(", ", m.killingTests());
            String searchBlob = (className + ' ' + method.methodName() + ' ' + m.mutator() + ' ' + m.status() + ' ' + killingTests)
                .toLowerCase(Locale.ROOT);
            sb.append("<tr class=\"mutant-row status-").append(esc(statusToken))
                .append("\" data-status=\"").append(esc(m.status())).append("\" data-search=\"").append(esc(searchBlob))
                .append("\"><td>").append(esc(m.mutator())).append("</td><td>").append(m.line())
                .append("</td><td>").append(esc(m.status())).append("</td><td>")
                .append(killingTests.isEmpty() ? "—" : esc(killingTests)).append("</td></tr>\n");
        }
        sb.append("</tbody>\n");
        closeTable(sb);
    }

    // ---- File coverage: Sonar-style folder tree + path filter ----

    private static final class TreeNode {
        final Map<String, TreeNode> children = new LinkedHashMap<>();
        final List<FileCoverageEntry> files = new ArrayList<>();
    }

    private record Agg(int numerator, int denominator) {
        static final Agg ZERO = new Agg(0, 0);

        Agg plus(Agg other) {
            return new Agg(numerator + other.numerator, denominator + other.denominator);
        }

        String percentText() {
            if (denominator == 0) {
                return "n/a";
            }
            BigDecimal percent = BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
            return percent + "%";
        }
    }

    private static void renderFileCoverage(StringBuilder sb, FileCoverageBlock block) {
        openSection(sb, "file-coverage", "Dosya bazlı kapsama (" + block.files().size() + " dosya)");
        sb.append("<div class=\"filter-bar\">\n")
            .append("<input type=\"text\" id=\"file-filter\" class=\"filter-input\" ")
            .append("placeholder=\"dosya yoluna göre filtrele\" oninput=\"coverdictFilterFileTree()\">\n")
            .append("</div>\n");

        Map<String, TreeNode> byModule = new LinkedHashMap<>();
        for (FileCoverageEntry entry : block.files()) {
            TreeNode moduleRoot = byModule.computeIfAbsent(entry.module(), k -> new TreeNode());
            insertIntoTree(moduleRoot, entry);
        }
        List<String> moduleIds = new ArrayList<>(byModule.keySet());
        Collections.sort(moduleIds);
        for (String moduleId : moduleIds) {
            renderTreeNode(sb, moduleId, byModule.get(moduleId), true);
        }

        if (!block.excluded().isEmpty()) {
            sb.append("<p>").append(block.excluded().size()).append(" hariç tutulan dosya.</p>\n");
        }
        closeSection(sb);
    }

    private static void insertIntoTree(TreeNode root, FileCoverageEntry entry) {
        String[] segments = entry.path().split("/");
        TreeNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            current = current.children.computeIfAbsent(segments[i], k -> new TreeNode());
        }
        current.files.add(entry);
    }

    /** Renders {@code node} as a collapsible {@code <details>} (its own subtree included) and returns its aggregate numerator/denominator sum, so a parent folder can sum its children's already-computed sums rather than re-deriving anything (hard rule 4). */
    private static Agg renderTreeNode(StringBuilder sb, String name, TreeNode node, boolean defaultOpen) {
        List<String> childNames = new ArrayList<>(node.children.keySet());
        Collections.sort(childNames);
        StringBuilder inner = new StringBuilder();
        Agg agg = Agg.ZERO;
        for (String childName : childNames) {
            agg = agg.plus(renderTreeNode(inner, childName, node.children.get(childName), false));
        }

        List<FileCoverageEntry> sortedFiles = node.files.stream().sorted(Comparator.comparing(FileCoverageEntry::path)).toList();
        for (FileCoverageEntry entry : sortedFiles) {
            Metric metric = entry.metrics().sonarCompatible();
            agg = agg.plus(new Agg(metric.numerator(), metric.denominator()));
            String percentText = metric.percent() == null ? "n/a" : metric.percent() + "%";
            inner.append("<div class=\"tree-file\" data-path=\"").append(esc(entry.path().toLowerCase(Locale.ROOT)))
                .append("\"><code>").append(esc(fileNameOnly(entry.path()))).append("</code>")
                .append("<span class=\"tree-pct\">").append(esc(percentText)).append("</span></div>\n");
        }

        sb.append("<details class=\"tree-folder\"").append(defaultOpen ? " open data-default-open=\"1\"" : "").append(">\n")
            .append("<summary>").append(esc(name)).append('/')
            .append(" <span class=\"tree-pct\">").append(esc(agg.percentText())).append("</span></summary>\n")
            .append(inner)
            .append("</details>\n");
        return agg;
    }

    private static String fileNameOnly(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    /**
     * A {@code null} field (an optional path/testMethod/productionMethod
     * the schema leaves absent for this row) renders as an em dash rather
     * than the literal text {@code "null"} - hard rule 3a: absence must
     * look like absence, not like a value.
     */
    private static String esc(String value) {
        if (value == null) {
            return "—";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F || (c >= 0x80 && c <= 0x9F)) {
                out.append(String.format("\\u%04x", (int) c));
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '"') {
                out.append("&quot;");
            } else if (c == '\'') {
                out.append("&#39;");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * D-77: three-tier theme token scheme (artifact-design convention) - a
     * light default on bare {@code :root}, a system-dark override guarded by
     * {@code :not([data-theme="light"])} so the manual toggle can still win,
     * and an explicit {@code [data-theme="dark"]} override that always wins
     * regardless of system preference. The dark token values are
     * necessarily repeated in both blocks (plain CSS custom properties have
     * no shared-constant mechanism across two separate rule blocks).
     */
    private static final String CSS = """
        :root {
          --paper: #f7f4ee; --paper-raised: #fffdf9; --ink: #1c1f26; --ink-soft: #565b64;
          --line: #ddd7c9; --accent: #1f3a5f; --accent-soft: #e4ecf3;
          --warn: #93400f; --warn-soft: #f4e3d1; --info: #2f5f45; --info-soft: #dcece2;
          --kill: #2f5f45; --survive: #93301c; --other: #7a7264;
          --shadow: 0 1px 2px rgba(28, 31, 38, 0.08);
          color-scheme: light;
        }
        @media (prefers-color-scheme: dark) {
          :root:not([data-theme="light"]) {
            --paper: #14161b; --paper-raised: #1b1e25; --ink: #eae6db; --ink-soft: #a19c8f;
            --line: #2c2f37; --accent: #86aed6; --accent-soft: #1d2a3a;
            --warn: #e0a06a; --warn-soft: #3a2a1a; --info: #8fc4a6; --info-soft: #1c2f24;
            --kill: #8fc4a6; --survive: #e08a76; --other: #8c8577;
            --shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
            color-scheme: dark;
          }
        }
        :root[data-theme="dark"] {
          --paper: #14161b; --paper-raised: #1b1e25; --ink: #eae6db; --ink-soft: #a19c8f;
          --line: #2c2f37; --accent: #86aed6; --accent-soft: #1d2a3a;
          --warn: #e0a06a; --warn-soft: #3a2a1a; --info: #8fc4a6; --info-soft: #1c2f24;
          --kill: #8fc4a6; --survive: #e08a76; --other: #8c8577;
          --shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
          color-scheme: dark;
        }
        :root[data-theme="light"] { color-scheme: light; }
        * { box-sizing: border-box; }
        body { margin: 0; background: var(--paper); color: var(--ink);
          font-family: "IBM Plex Sans", system-ui, sans-serif; line-height: 1.5; }
        main { max-width: 1100px; margin: 0 auto; padding: 2rem 1.25rem 4rem; }
        h1, h2, h3 { font-family: "IBM Plex Serif", Georgia, serif; }
        h4 { margin: 1rem 0 0.25rem; font-size: 0.95em; }
        code { font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 0.9em; }
        header { position: relative; padding-right: 9rem; }
        .generated-at { color: var(--ink-soft); font-size: 0.9em; margin: -0.5rem 0 0.75rem; }
        table { width: max-content; min-width: 100%; border-collapse: collapse; margin: 0; background: var(--paper-raised); }
        th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid var(--line); overflow-wrap: break-word; }
        /* Bir path/identifier (<code> içindeki her şey) kelime ortasından
           çirkin bir yerden bölünmez - gerekirse kendi table-wrap'i içinde
           yatay kayar; sade metin (mesaj/öneri sütunları) kelime sınırında
           doğal olarak sarılır. */
        td code, th code { white-space: nowrap; }
        th { color: var(--ink-soft); font-weight: 600; font-size: 0.85em; text-transform: uppercase; white-space: nowrap; }
        /* Bir tablonun içeriği main'in genişliğini aşarsa sayfa değil, sadece
           bu sarmalayıcı yatay kaydırılır (bir uzun yol/mesaj tüm sayfayı
           çarpıtmasın diye). */
        .table-wrap { overflow-x: auto; margin: 0.5rem 0 1.25rem; border: 1px solid var(--line); border-radius: 6px; padding: 0.5rem; }
        .table-wrap table { margin: 0; }
        section { margin-bottom: 2rem; }
        details.report-section { margin-bottom: 1.5rem; }
        details.report-section > summary { cursor: pointer; list-style: revert; }
        details.report-section > summary h2 { display: inline; }
        details.report-section > .report-section-body { margin-top: 0.75rem; }
        .status { display: inline-block; padding: 0.15rem 0.6rem; border-radius: 999px; font-weight: 600; }
        .status-complete { background: var(--info-soft); color: var(--info); }
        .status-incomplete { background: var(--warn-soft); color: var(--warn); }
        .unavailable { color: var(--ink-soft); font-style: italic; }
        .identity { display: grid; grid-template-columns: max-content 1fr; gap: 0.2rem 0.75rem; color: var(--ink-soft); }
        .identity dt { font-weight: 600; }
        footer { color: var(--ink-soft); font-size: 0.85em; border-top: 1px solid var(--line); padding-top: 1rem; }
        .filter-bar { display: flex; align-items: center; gap: 1rem; margin: 0.5rem 0 1rem; flex-wrap: wrap; }
        .filter-input { display: block; width: 100%; padding: 0.4rem 0.6rem; margin-bottom: 0.5rem; border: 1px solid var(--line);
          border-radius: 6px; background: var(--paper-raised); color: var(--ink); font-family: inherit; box-sizing: border-box; }
        .filter-bar .filter-input { display: inline-block; width: auto; flex: 1 1 20rem; margin-bottom: 0; }
        .mutation-total { color: var(--ink-soft); }
        details.mutation-class { background: var(--paper-raised); border: 1px solid var(--line); border-radius: 6px;
          padding: 0.5rem 0.75rem; margin-bottom: 0.5rem; }
        details.mutation-class summary { cursor: pointer; font-weight: 600; }
        table.mutants td:nth-child(3) { font-weight: 600; }
        tr.status-KILLED td:nth-child(3) { color: var(--kill); }
        tr.status-SURVIVED td:nth-child(3) { color: var(--survive); }
        details.tree-folder { margin-left: 0.25rem; border-left: 1px dashed var(--line); padding-left: 0.75rem; }
        details.tree-folder summary { cursor: pointer; }
        .tree-file { display: flex; justify-content: space-between; gap: 1rem; padding: 0.15rem 0 0.15rem 1rem;
          border-bottom: 1px dotted var(--line); }
        .tree-pct { color: var(--ink-soft); font-variant-numeric: tabular-nums; }
        .theme-toggle { position: absolute; top: 0; right: 0; display: inline-flex; align-items: center;
          gap: 0.4rem; padding: 0.35rem 0.75rem; border-radius: 999px; border: 1px solid var(--line);
          background: var(--paper-raised); color: var(--ink); font-family: inherit; font-size: 0.85em;
          cursor: pointer; box-shadow: var(--shadow); }
        .theme-toggle:hover { border-color: var(--accent); }
        """;

    /**
     * Static source text, no interpolation - see the class javadoc's D-76
     * note. Reads only pre-escaped {@code data-*} attributes already in the
     * DOM (never {@code innerHTML}/{@code eval} on any of them) and toggles
     * {@code style.display}/{@code open}.
     */
    private static final String SCRIPT = """
        function coverdictEffectiveTheme() {
          var explicit = document.documentElement.getAttribute('data-theme');
          if (explicit === 'light' || explicit === 'dark') { return explicit; }
          return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light';
        }

        function coverdictUpdateThemeButton() {
          var btn = document.getElementById('theme-toggle');
          if (!btn) { return; }
          var isDark = coverdictEffectiveTheme() === 'dark';
          btn.textContent = isDark ? '\\u2600\\uFE0F Aç\\u0131k Mod' : '\\uD83C\\uDF19 Koyu Mod';
          btn.setAttribute('aria-pressed', String(isDark));
        }

        function coverdictSetTheme(theme) {
          document.documentElement.setAttribute('data-theme', theme);
          try { window.localStorage.setItem('coverdict-report-theme', theme); } catch (e) { /* file:// veya gizli sekmede engellenebilir - sorun değil */ }
          coverdictUpdateThemeButton();
        }

        function coverdictToggleTheme() {
          coverdictSetTheme(coverdictEffectiveTheme() === 'dark' ? 'light' : 'dark');
        }

        (function coverdictRestoreTheme() {
          try {
            var saved = window.localStorage.getItem('coverdict-report-theme');
            if (saved === 'light' || saved === 'dark') { document.documentElement.setAttribute('data-theme', saved); }
          } catch (e) { /* aynı, sorun değil - varsayılan sistem temasında kal */ }
          coverdictUpdateThemeButton();
        })();

        /**
         * D-79: one generic row/list-item filter reused by every plain
         * table or list section - matches the input's value against each
         * descendant [data-search] element's own pre-built, already-escaped
         * search text within the nearest .filterable ancestor. Mutation and
         * file-coverage keep their own bespoke functions below (extra
         * status-checkbox / folder-cascade logic this generic one does not
         * need).
         */
        function coverdictFilterRows(input) {
          var scope = input.closest('.filterable');
          if (!scope) { return; }
          var q = input.value.trim().toLowerCase();
          scope.querySelectorAll('[data-search]').forEach(function (row) {
            row.style.display = (q === '' || row.dataset.search.indexOf(q) !== -1) ? '' : 'none';
          });
        }

        function coverdictFilterMutation() {
          var filterInput = document.getElementById('mutation-filter');
          var survivedOnly = document.getElementById('mutation-survived-only');
          if (!filterInput || !survivedOnly) { return; }
          var q = filterInput.value.trim().toLowerCase();
          var onlySurvived = survivedOnly.checked;
          var rows = document.querySelectorAll('#mutation .mutant-row');
          rows.forEach(function (row) {
            var textMatch = q === '' || row.dataset.search.indexOf(q) !== -1;
            var statusMatch = !onlySurvived || row.dataset.status === 'SURVIVED';
            row.style.display = (textMatch && statusMatch) ? '' : 'none';
          });
          var groups = document.querySelectorAll('#mutation details.mutation-class');
          var filtering = q !== '' || onlySurvived;
          groups.forEach(function (group) {
            var visibleRows = group.querySelectorAll('.mutant-row');
            var anyVisible = false;
            for (var i = 0; i < visibleRows.length; i++) {
              if (visibleRows[i].style.display !== 'none') { anyVisible = true; break; }
            }
            group.style.display = (!filtering || anyVisible) ? '' : 'none';
            if (filtering && anyVisible) { group.open = true; }
            if (!filtering) { group.open = false; }
          });
        }

        function coverdictFilterFileTree() {
          var filterInput = document.getElementById('file-filter');
          if (!filterInput) { return; }
          var q = filterInput.value.trim().toLowerCase();
          var files = document.querySelectorAll('#file-coverage .tree-file');
          files.forEach(function (file) {
            var match = q === '' || file.dataset.path.indexOf(q) !== -1;
            file.style.display = match ? '' : 'none';
          });
          var folders = document.querySelectorAll('#file-coverage details.tree-folder');
          folders.forEach(function (folder) {
            if (q === '') {
              folder.style.display = '';
              folder.open = folder.hasAttribute('data-default-open');
              return;
            }
            var descendantFiles = folder.querySelectorAll('.tree-file');
            var anyVisible = false;
            for (var i = 0; i < descendantFiles.length; i++) {
              if (descendantFiles[i].style.display !== 'none') { anyVisible = true; break; }
            }
            folder.style.display = anyVisible ? '' : 'none';
            if (anyVisible) { folder.open = true; }
          });
        }
        """;
}
