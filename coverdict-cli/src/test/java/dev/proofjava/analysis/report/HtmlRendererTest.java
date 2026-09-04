package dev.proofjava.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import dev.proofjava.analysis.jacoco.LineCoverage;
import dev.proofjava.analysis.metrics.MetricsEngine;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.Confidence;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.Severity;
import dev.proofjava.analysis.mutation.Mutant;
import dev.proofjava.analysis.mutation.MutatedMethod;
import dev.proofjava.analysis.mutation.MutationModuleEvidence;
import dev.proofjava.analysis.pertest.PerTestEntry;
import dev.proofjava.analysis.pertest.PerTestLine;
import dev.proofjava.analysis.pertest.PerTestModuleEvidence;
import dev.proofjava.analysis.vcs.VcsIdentity;

/**
 * D-80: the renderer now embeds one JSON object ({@link ReportDataWriter})
 * instead of printing every row as HTML server-side, so these tests read
 * that JSON back (via {@link #parseData(String)}, a small jackson-core
 * streaming reader since the project only depends on jackson-core, not
 * jackson-databind) rather than grepping for table markup.
 *
 * <p>SECURITY-POLICY.md #4 (extended to HTML output): a control character,
 * an HTML metacharacter, or a {@code </script>} sequence in any
 * doc-derived field never lets the embedded JSON break out of its
 * {@code <script>} element - see {@link HtmlRenderer}'s class javadoc for
 * why {@code \\u003c}-style escaping, not HTML-entity escaping, is the
 * correct defense for content inside a raw-text element.
 */
class HtmlRendererTest {

    private ModuleInput module() {
        return new ModuleInput("root", ".", List.of("src/main/java"), List.of("src/test/java"), List.of());
    }

    private VerdictDocument baseDoc(boolean complete, List<AnalysisReason> incompleteReasons, List<Finding> findings) {
        return new VerdictDocument("0.1.0", "0.1.0-TEST", complete, incompleteReasons,
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), findings, List.of());
    }

    // ---- JSON extraction helper (jackson-core streaming; no jackson-databind dependency) ----

    private static Map<String, Object> parseData(String rendered) throws IOException {
        String marker = "id=\"coverdict-data\" type=\"application/json\">";
        int start = rendered.indexOf(marker);
        assertTrue(start >= 0, "coverdict-data script tag not found: " + rendered);
        start += marker.length();
        int end = rendered.indexOf("</script>", start);
        String embedded = rendered.substring(start, end);
        JsonFactory factory = new JsonFactory();
        try (JsonParser p = factory.createParser(embedded)) {
            p.nextToken();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) readValue(p);
            return result;
        }
    }

    private static Object readValue(JsonParser p) throws IOException {
        return switch (p.currentToken()) {
            case START_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String key = p.currentName();
                    p.nextToken();
                    map.put(key, readValue(p));
                }
                yield map;
            }
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readValue(p));
                }
                yield list;
            }
            case VALUE_STRING -> p.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> p.getNumberValue();
            case VALUE_TRUE, VALUE_FALSE -> p.getBooleanValue();
            case VALUE_NULL -> null;
            default -> throw new IllegalStateException("unexpected token: " + p.currentToken());
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> arr(Object o) {
        return (List<Object>) o;
    }

    @Test
    void aScriptBreakoutAttemptInAFindingCannotEscapeTheEmbeddedJson() throws IOException {
        char esc = 0x1b;
        char bel = 0x07;
        String maliciousPath = "src/test/java/</script><script>alert(1)</script>/" + esc + "Evil.java";
        String maliciousMessage = "bad \"quote\" & <b>bold</b> " + bel;
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            maliciousPath, 1, 1, "com.example.EvilTest#m()", maliciousMessage, "suggestion", "0123456789abcdef", null);

        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of(finding)));

        // The literal bytes that would end the <script> element must never occur.
        assertFalse(rendered.toLowerCase(java.util.Locale.ROOT).contains("</script><script>alert"), rendered);
        assertFalse(rendered.contains("<b>bold</b>"), rendered);
        assertEquals(1, countOccurrences(rendered, "<script id=\"coverdict-data\""), rendered);
        assertEquals(1, countOccurrences(rendered.substring(rendered.indexOf("<script>\n")), "</script>"), rendered);

        // ... yet JSON.parse (simulated here by the same streaming reader a browser's JSON.parse would agree with)
        // recovers the exact original content - nothing was corrupted, only relocated out of harm's way.
        Map<String, Object> data = parseData(rendered);
        Map<String, Object> f = arr(obj(data.get("findings")).get("items")).stream()
            .map(HtmlRendererTest::obj).findFirst().orElseThrow();
        assertEquals(maliciousPath, f.get("path"));
        assertEquals(maliciousMessage, f.get("message"));
    }

    @Test
    void rendersACompleteNoVcsDocument() throws IOException {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertTrue(rendered.startsWith("<!doctype html>"), rendered);
        Map<String, Object> data = parseData(rendered);
        Map<String, Object> meta = obj(data.get("meta"));
        assertEquals(Boolean.TRUE, meta.get("complete"));
        assertEquals("complete", meta.get("statusLabel"));
        assertEquals("0.1.0-TEST", meta.get("toolVersion"));
        assertTrue(arr(obj(data.get("findings")).get("items")).isEmpty());
    }

    @Test
    void rendersAnIncompleteDocumentWithoutSilentlyDroppingTheReason() throws IOException {
        AnalysisReason reason = new AnalysisReason("SOME_CODE", "bad " + (char) 0x1b + "[0m message");
        String rendered = HtmlRenderer.render(baseDoc(false, List.of(reason), List.of()));

        assertFalse(rendered.indexOf((char) 0x1b) >= 0, rendered);
        Map<String, Object> data = parseData(rendered);
        Map<String, Object> meta = obj(data.get("meta"));
        assertEquals(Boolean.FALSE, meta.get("complete"));
        Map<String, Object> incomplete = obj(arr(data.get("incompleteReasons")).get(0));
        assertEquals("SOME_CODE", incomplete.get("code"));
        // JSON.parse recovers the exact original message, escape character included - only the raw HTML source
        // (asserted above) is free of the literal byte, never the parsed content.
        assertEquals(reason.message(), incomplete.get("message"));
    }

    @Test
    void unavailableNewCodeStatusIsShownLiterallyNeverBlank() throws IOException {
        Map<String, Object> data = parseData(HtmlRenderer.render(baseDoc(true, List.of(), List.of())));
        Map<String, Object> newCode = obj(obj(data.get("coverage")).get("newCode"));
        assertEquals(Boolean.FALSE, newCode.get("available"));
        assertEquals("unavailable_no_vcs", newCode.get("unavailableStatus"));
    }

    @Test
    void optionalBlocksAreAbsentWhenNull() throws IOException {
        Map<String, Object> data = parseData(HtmlRenderer.render(baseDoc(true, List.of(), List.of())));

        assertNull(data.get("perTest"));
        assertNull(data.get("mutation"));
        assertNull(data.get("fileCoverage"));
        // changedFiles is a schema-guaranteed list, not an optional block - always present, even empty (D-80 fix).
        assertNotNull(data.get("changedFiles"));
        assertTrue(arr(data.get("changedFiles")).isEmpty());
    }

    @Test
    void optionalBlocksRenderWhenPresent() throws IOException {
        PerTestModuleEvidence perTestModule = new PerTestModuleEvidence("root",
            List.of(new PerTestEntry("com.example.Calculator", "add", List.of(new PerTestLine(10, List.of("com.example.CalculatorTest#add()"))))),
            List.of());
        Mutant killed = new Mutant("RETURNS", 10, "KILLED", List.of("com.example.CalculatorTest#add()"));
        Mutant survived = new Mutant("RETURNS", 11, "SURVIVED", List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calculator", "add", "(II)I", 10, 11, List.of(killed, survived));
        MutationModuleEvidence mutationModule = new MutationModuleEvidence("root", List.of(method), List.of());
        FileCoverageEntry fileEntry = new FileCoverageEntry("root", "src/main/java/com/example/Calculator.java",
            MetricsEngine.compute(List.of()), List.of(new LineCoverage(10, 0, 3, 0, 0)));
        FileCoverageBlock fileCoverage = new FileCoverageBlock(List.of(fileEntry), List.of());

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of(),
            List.of(perTestModule), List.of(mutationModule), fileCoverage);

        Map<String, Object> data = parseData(HtmlRenderer.render(doc));

        assertNotNull(data.get("perTest"));
        assertNotNull(data.get("mutation"));
        assertNotNull(data.get("fileCoverage"));
        assertEquals("root", obj(arr(data.get("perTest")).get(0)).get("moduleId"));
        Map<String, Object> file0 = obj(arr(obj(data.get("fileCoverage")).get("files")).get(0));
        assertEquals("src/main/java/com/example/Calculator.java", file0.get("path"));
        // module "root" declares sourceRoots=["src/main/java"] with root="." - displayPath strips that prefix.
        assertEquals("com/example/Calculator.java", file0.get("displayPath"));
    }

    @Test
    void producesStructurallyBalancedHtml() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        long openHtml = countOccurrences(rendered, "<html");
        long closeHtml = countOccurrences(rendered, "</html>");
        long openBody = countOccurrences(rendered, "<body");
        long closeBody = countOccurrences(rendered, "</body>");
        assertTrue(openHtml == 1 && closeHtml == 1, rendered);
        assertTrue(openBody == 1 && closeBody == 1, rendered);
        assertFalse(rendered.contains("fonts.googleapis.com"), rendered);
    }

    @Test
    void mutationSectionCarriesPerMutantDataGroupedByClassWithNoOtherBucket() throws IOException {
        Mutant killed = new Mutant("RETURNS", 10, "KILLED", List.of("com.example.CalculatorTest#add()"));
        Mutant survived = new Mutant("RETURNS", 11, "SURVIVED", List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calculator", "add", "(II)I", 10, 11, List.of(killed, survived));
        MutationModuleEvidence mutationModule = new MutationModuleEvidence("root", List.of(method), List.of());

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of(),
            null, List.of(mutationModule));

        Map<String, Object> data = parseData(HtmlRenderer.render(doc));
        Map<String, Object> mutation = obj(data.get("mutation"));
        Map<String, Object> totals = obj(mutation.get("totalsByStatus"));
        assertEquals(1, ((Number) totals.get("KILLED")).intValue());
        assertEquals(1, ((Number) totals.get("SURVIVED")).intValue());
        assertNull(totals.get("NO_COVERAGE"));

        Map<String, Object> mod = obj(arr(mutation.get("modules")).get(0));
        Map<String, Object> cls = obj(arr(mod.get("classes")).get(0));
        assertEquals("com.example.Calculator", cls.get("className"));
        List<Object> mutants = arr(obj(arr(cls.get("methods")).get(0)).get("mutants"));
        assertEquals(2, mutants.size());
        assertEquals("KILLED", obj(mutants.get(0)).get("status"));
        assertEquals("SURVIVED", obj(mutants.get(1)).get("status"));
        assertEquals(List.of("com.example.CalculatorTest#add()"), arr(obj(mutants.get(0)).get("killingTests")));

        // Every status this codebase can produce has a friendly label available.
        assertNotNull(obj(data.get("labels")).get("KILLED"));
        assertNotNull(obj(data.get("labels")).get("SURVIVED"));
    }

    @Test
    void fileCoverageIsAFlatSourceRootRelativeListNotANestedTree() throws IOException {
        FileCoverageEntry a = new FileCoverageEntry("root", "src/main/java/com/example/pkg/A.java",
            MetricsEngine.compute(List.of()), List.of());
        FileCoverageEntry b = new FileCoverageEntry("root", "src/main/java/com/example/pkg/B.java",
            MetricsEngine.compute(List.of()), List.of());
        FileCoverageEntry rootFile = new FileCoverageEntry("root", "src/main/java/Root.java",
            MetricsEngine.compute(List.of()), List.of());
        FileCoverageBlock fileCoverage = new FileCoverageBlock(List.of(a, b, rootFile), List.of("excluded/Gen.java"));

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of(),
            null, null, fileCoverage);

        Map<String, Object> data = parseData(HtmlRenderer.render(doc));
        Map<String, Object> fc = obj(data.get("fileCoverage"));
        assertEquals(3, ((Number) fc.get("totalFiles")).intValue());
        assertEquals(List.of("excluded/Gen.java"), arr(fc.get("excluded")));

        // D-81 (dashboard rewrite): no more nested/compressed folder tree - a flat list, sorted by the full
        // repo-relative path ("Root.java" sorts before "com/..." - uppercase 'R' < lowercase 'c' in ASCII),
        // each entry carrying displayPath/packagePath/fileName stripped of its module's own source-root prefix.
        List<Object> files = arr(fc.get("files"));
        assertEquals(3, files.size());
        Map<String, Object> root0 = obj(files.get(0));
        assertEquals("Root.java", root0.get("displayPath"));
        assertEquals("", root0.get("packagePath"));
        assertEquals("Root.java", root0.get("fileName"));
        Map<String, Object> fileA = obj(files.get(1));
        assertEquals("com/example/pkg/A.java", fileA.get("displayPath"));
        assertEquals("com/example/pkg", fileA.get("packagePath"));
        assertEquals("A.java", fileA.get("fileName"));
    }

    @Test
    void theEmittedScriptIsStaticAndNeverBuildsMarkupFromRawHtml() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        long openScript = countOccurrences(rendered, "<script>");
        long closeScriptTotal = countOccurrences(rendered, "</script>");
        assertTrue(openScript == 1, rendered);
        // one closing tag for the data script, one for the code script.
        assertEquals(2, closeScriptTotal, rendered);
        assertFalse(rendered.contains("innerHTML"), rendered);
        assertFalse(rendered.contains("eval("), rendered);
    }

    @Test
    void themeControlIsPresentAndOpensLightByDefault() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertTrue(rendered.contains("'theme-toggle'"), rendered);
        assertTrue(rendered.contains("function toggleTheme"), rendered);
        assertTrue(rendered.contains(":root[data-theme=\"dark\"]"), rendered);
        assertTrue(rendered.contains("coverdict-report-theme"), rendered);
        // D-81: the user asked the report to open in light mode first - bare :root carries the light
        // token values directly and there is no media query auto-switching to dark on system preference,
        // so a first-time reader (no stored preference) always opens light regardless of OS theme.
        assertFalse(rendered.contains("@media (prefers-color-scheme"), rendered);
    }

    @Test
    void noscriptFallbackExplainsTheBlankPageRatherThanShowingNothing() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertTrue(rendered.contains("<noscript>"), rendered);
        assertTrue(rendered.contains("JavaScript"), rendered);
    }

    @Test
    void headerCarriesGeneratedAtModulesAndSettingsInsteadOfARawIdentityDump() throws IOException {
        VcsIdentity identity = new VcsIdentity("b3f4ca20087f9066de4c340522ff84e0558e1ad1", "main",
            "b3f4ca20087f9066de4c340522ff84e0558e1ad1", "b3f4ca20087f9066de4c340522ff84e0558e1ad1", true);
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of("**/generated/**"), List.of(module()), "base-ref", "all", identity,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of());

        Map<String, Object> data = parseData(HtmlRenderer.render(doc));
        Map<String, Object> meta = obj(data.get("meta"));

        assertNotNull(meta.get("generatedAt"));
        assertEquals("root (.)", meta.get("modules"));
        assertEquals("diff against the given ref (base-ref)", meta.get("diffMode"));
        assertEquals("all test files", meta.get("findingsScopeLabel"));
        assertEquals(List.of("**/generated/**"), arr(meta.get("exclusions")));
        assertEquals(Boolean.TRUE, meta.get("dirty"));

        // base == mergeBase == head: a single deduplicated "Commit" row, short hash, full hash kept alongside it.
        List<Object> commitRows = arr(meta.get("commitRows"));
        assertEquals(1, commitRows.size());
        Map<String, Object> row = obj(commitRows.get(0));
        assertEquals("Commit", row.get("label"));
        assertEquals("b3f4ca2", row.get("short"));
        assertEquals("b3f4ca20087f9066de4c340522ff84e0558e1ad1", row.get("full"));
    }

    @Test
    void headerCarriesThreeSeparateCommitRowsWhenBaseMergeBaseAndHeadDiffer() throws IOException {
        VcsIdentity identity = new VcsIdentity("1111111111111111111111111111111111111a", "feature",
            "2222222222222222222222222222222222222b", "3333333333333333333333333333333333333c", false);
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "base-ref", "all", identity,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of());

        Map<String, Object> data = parseData(HtmlRenderer.render(doc));
        List<Object> commitRows = arr(obj(data.get("meta")).get("commitRows"));
        List<String> labels = commitRows.stream().map(o -> (String) obj(o).get("label")).toList();
        assertEquals(List.of("Base commit", "Merge-base", "HEAD"), labels);
    }

    @Test
    void changedFilesFindingsAndReasonsCarryLowercasedSearchBlobs() throws IOException {
        ChangedFile file = new ChangedFile("src/main/java/Foo.java", "root", Classification.MAPPED, 5, 3, List.of());
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            "src/test/FooTest.java", 1, 1, "com.example.FooTest#m()", "no assertions here", "add one", "0123456789abcdef", null);
        AnalysisReason warning = new AnalysisReason("SOME_CODE", "a warning message");

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "uncommitted", "all", null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"),
            List.of(file), List.of(finding), List.of(warning));

        Map<String, Object> data = parseData(HtmlRenderer.render(doc));

        Map<String, Object> changedFile = obj(arr(data.get("changedFiles")).get(0));
        assertEquals("root src/main/java/foo.java mapped", changedFile.get("search"));

        Map<String, Object> findingRow = arr(obj(data.get("findings")).get("items")).stream()
            .map(HtmlRendererTest::obj).findFirst().orElseThrow();
        assertEquals("no_recognized_oracle warning high src/test/footest.java com.example.footest#m() no assertions here add one",
            findingRow.get("search"));

        Map<String, Object> warningRow = obj(arr(data.get("warnings")).get(0));
        assertEquals("some_code a warning message", warningRow.get("search"));
    }

    @Test
    void everyRuleIdIsListedEvenWhenItHasZeroFindings() throws IOException {
        Map<String, Object> data = parseData(HtmlRenderer.render(baseDoc(true, List.of(), List.of())));
        List<Object> ruleIds = arr(data.get("ruleIds"));
        assertTrue(ruleIds.contains("NO_RECOGNIZED_ORACLE"));
        assertTrue(ruleIds.contains("SUBSUMED_TEST"));
        assertEquals(dev.proofjava.analysis.model.RuleIds.ALL.size(), ruleIds.size());
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
