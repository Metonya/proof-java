package dev.coverdict.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.coverdict.analysis.jacoco.LineCoverage;
import dev.coverdict.analysis.metrics.MetricsEngine;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.Severity;
import dev.coverdict.analysis.mutation.Mutant;
import dev.coverdict.analysis.mutation.MutatedMethod;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;
import dev.coverdict.analysis.pertest.PerTestEntry;
import dev.coverdict.analysis.pertest.PerTestLine;
import dev.coverdict.analysis.pertest.PerTestModuleEvidence;
import dev.coverdict.analysis.vcs.VcsIdentity;

/** SECURITY-POLICY.md #4 (extended to HTML output): a control character or an HTML metacharacter in any rendered field never reaches the browser raw. */
class HtmlRendererTest {

    private ModuleInput module() {
        return new ModuleInput("root", ".", List.of("src/main/java"), List.of("src/test/java"), List.of());
    }

    private VerdictDocument baseDoc(boolean complete, List<AnalysisReason> incompleteReasons, List<Finding> findings) {
        return new VerdictDocument("0.1.0", "0.1.0-TEST", complete, incompleteReasons,
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), findings, List.of());
    }

    @Test
    void controlCharactersAndHtmlMetacharactersInAFindingAreEscapedNotRenderedRaw() {
        char esc = 0x1b;
        char bel = 0x07;
        String escapedEsc = "\\" + "u001b";
        String escapedBel = "\\" + "u0007";
        String maliciousPath = "src/test/java/<script>alert(1)</script>/" + esc + "Evil.java";
        String maliciousMessage = "bad \"quote\" & <b>bold</b> " + bel;
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            maliciousPath, 1, 1, "com.example.EvilTest#m()", maliciousMessage, "suggestion", "0123456789abcdef", null);

        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of(finding)));

        assertFalse(rendered.contains("<script>alert"), rendered);
        assertFalse(rendered.contains("<b>bold</b>"), rendered);
        assertFalse(rendered.indexOf(esc) >= 0, rendered);
        assertFalse(rendered.indexOf(bel) >= 0, rendered);
        assertTrue(rendered.contains("&lt;script&gt;"), rendered);
        assertTrue(rendered.contains("&amp;"), rendered);
        assertTrue(rendered.contains("&quot;quote&quot;"), rendered);
        assertTrue(rendered.contains(escapedEsc), rendered);
        assertTrue(rendered.contains(escapedBel), rendered);
    }

    @Test
    void rendersACompleteNoVcsDocument() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertTrue(rendered.startsWith("<!doctype html>"), rendered);
        assertTrue(rendered.contains("status-complete"), rendered);
        assertTrue(rendered.contains("Bulgu yok."), rendered);
        assertTrue(rendered.contains("0.1.0-TEST"), rendered);
    }

    @Test
    void rendersAnIncompleteDocumentWithoutSilentlyDroppingTheReason() {
        AnalysisReason reason = new AnalysisReason("SOME_CODE", "bad " + (char) 0x1b + "[0m message");
        String rendered = HtmlRenderer.render(baseDoc(false, List.of(reason), List.of()));

        assertTrue(rendered.contains("status-incomplete"), rendered);
        assertTrue(rendered.contains("SOME_CODE"), rendered);
        assertFalse(rendered.indexOf((char) 0x1b) >= 0, rendered);
    }

    @Test
    void unavailableNewCodeStatusIsShownLiterallyNeverBlank() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertTrue(rendered.contains("unavailable_no_vcs"), rendered);
    }

    @Test
    void optionalBlocksAreAbsentWhenNull() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertFalse(rendered.contains("Test bazlı kanıt"), rendered);
        assertFalse(rendered.contains("Mutasyon kanıtı"), rendered);
        assertFalse(rendered.contains("Dosya bazlı kapsama"), rendered);
    }

    @Test
    void optionalBlocksRenderWhenPresent() {
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

        String rendered = HtmlRenderer.render(doc);

        assertTrue(rendered.contains("Test bazlı kanıt"), rendered);
        assertTrue(rendered.contains("Mutasyon kanıtı"), rendered);
        assertTrue(rendered.contains("Dosya bazlı kapsama"), rendered);
        assertTrue(rendered.contains("Calculator.java"), rendered);
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
    void mutationSectionRendersPerMutantRowsGroupedByClassWithFilterControls() {
        Mutant killed = new Mutant("RETURNS", 10, "KILLED", List.of("com.example.CalculatorTest#add()"));
        Mutant survived = new Mutant("RETURNS", 11, "SURVIVED", List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calculator", "add", "(II)I", 10, 11, List.of(killed, survived));
        MutationModuleEvidence mutationModule = new MutationModuleEvidence("root", List.of(method), List.of());

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of(),
            null, List.of(mutationModule));

        String rendered = HtmlRenderer.render(doc);

        assertTrue(rendered.contains("id=\"mutation-filter\""), rendered);
        assertTrue(rendered.contains("id=\"mutation-survived-only\""), rendered);
        assertTrue(rendered.contains("class=\"mutation-class\""), rendered);
        assertTrue(rendered.contains("com.example.Calculator"), rendered);
        assertTrue(rendered.contains("data-status=\"KILLED\""), rendered);
        assertTrue(rendered.contains("data-status=\"SURVIVED\""), rendered);
        assertTrue(rendered.contains("class=\"mutant-row status-KILLED\""), rendered);
        assertTrue(rendered.contains("class=\"mutant-row status-SURVIVED\""), rendered);
        assertTrue(rendered.contains("com.example.CalculatorTest#add()"), rendered);
        assertTrue(rendered.contains("1</strong> KILLED"), rendered);
        assertTrue(rendered.contains("1</strong> SURVIVED"), rendered);
    }

    @Test
    void fileCoverageRendersANestedFolderTreeWithAggregatePercentagesAndFilterInput() {
        FileCoverageEntry a = new FileCoverageEntry("root", "src/main/java/com/example/pkg/A.java",
            MetricsEngine.compute(List.of()), List.of());
        FileCoverageEntry b = new FileCoverageEntry("root", "src/main/java/com/example/pkg/B.java",
            MetricsEngine.compute(List.of()), List.of());
        FileCoverageEntry root = new FileCoverageEntry("root", "src/main/java/Root.java",
            MetricsEngine.compute(List.of()), List.of());
        FileCoverageBlock fileCoverage = new FileCoverageBlock(List.of(a, b, root), List.of("excluded/Gen.java"));

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "no-vcs", "all", null, MetricsEngine.compute(List.of()),
            NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of(),
            null, null, fileCoverage);

        String rendered = HtmlRenderer.render(doc);

        assertTrue(rendered.contains("id=\"file-filter\""), rendered);
        assertTrue(rendered.contains("class=\"tree-folder\""), rendered);
        assertTrue(rendered.contains("data-default-open=\"1\""), rendered);
        assertTrue(rendered.contains("<summary>pkg/"), rendered);
        assertTrue(rendered.contains("data-path=\"src/main/java/com/example/pkg/a.java\""), rendered);
        assertTrue(rendered.contains("<code>A.java</code>"), rendered);
        assertTrue(rendered.contains("<code>Root.java</code>"), rendered);
        assertTrue(rendered.contains("1 hariç tutulan dosya."), rendered);
    }

    @Test
    void theEmittedScriptIsStaticAndNeverEchoesInputDerivedContentVerbatim() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        long openScript = countOccurrences(rendered, "<script>");
        long closeScript = countOccurrences(rendered, "</script>");
        assertTrue(openScript == 1 && closeScript == 1, rendered);
        assertTrue(rendered.contains("coverdictFilterMutation"), rendered);
        assertTrue(rendered.contains("coverdictFilterFileTree"), rendered);
        assertFalse(rendered.contains("innerHTML"), rendered);
        assertFalse(rendered.contains("eval("), rendered);
    }

    @Test
    void everyTableIsWrappedForHorizontalScrollInsteadOfBlowingOutThePage() {
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            "src/test/java/com/example/VeryLongPackageName/AnotherVeryLongSegment/EvilTest.java", 1, 1,
            "com.example.EvilTest#m()", "message", "suggestion", "0123456789abcdef", null);
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of(finding)));

        long openWraps = countOccurrences(rendered, "class=\"table-wrap");
        long openTables = countOccurrences(rendered, "<table");
        assertTrue(openWraps >= 1, rendered);
        assertTrue(openWraps == openTables, rendered);
    }

    @Test
    void themeToggleButtonIsPresentWithBothExplicitThemeCssBlocks() {
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of()));

        assertTrue(rendered.contains("id=\"theme-toggle\""), rendered);
        assertTrue(rendered.contains("onclick=\"coverdictToggleTheme()\""), rendered);
        assertTrue(rendered.contains(":root[data-theme=\"dark\"]"), rendered);
        assertTrue(rendered.contains(":root[data-theme=\"light\"]"), rendered);
        assertTrue(rendered.contains("coverdictSetTheme"), rendered);
        assertTrue(rendered.contains("coverdict-report-theme"), rendered);
    }

    @Test
    void everySectionIsACollapsibleDetailsOpenByDefault() {
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            "src/test/FooTest.java", 1, 1, "com.example.FooTest#m()", "message", "suggestion", "0123456789abcdef", null);
        String rendered = HtmlRenderer.render(baseDoc(true, List.of(), List.of(finding)));

        long reportSections = countOccurrences(rendered, "class=\"report-section\"");
        long openReportSections = countOccurrences(rendered, "<details open class=\"report-section\"");
        assertTrue(reportSections >= 2, rendered);
        assertEquals(reportSections, openReportSections, rendered);
    }

    @Test
    void headerShowsGeneratedAtTimestampModulesAndSettingsInsteadOfARawIdentityDump() throws java.io.IOException {
        VcsIdentity identity = new VcsIdentity("b3f4ca20087f9066de4c340522ff84e0558e1ad1", "main",
            "b3f4ca20087f9066de4c340522ff84e0558e1ad1", "b3f4ca20087f9066de4c340522ff84e0558e1ad1", true);
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of("**/generated/**"), List.of(module()), "base-ref", "all", identity,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of());

        String rendered = HtmlRenderer.render(doc);

        assertTrue(rendered.contains("Rapor oluşturulma zamanı"), rendered);
        assertTrue(rendered.contains("root (.)"), rendered);
        assertTrue(rendered.contains("belirtilen referansla fark"), rendered);
        assertTrue(rendered.contains("tüm test dosyaları"), rendered);
        assertTrue(rendered.contains("**/generated/**"), rendered);
        // base == mergeBase == head: a single deduplicated "Commit" row, short hash, full hash only in the tooltip.
        assertTrue(rendered.contains("<dt>Commit</dt>"), rendered);
        assertFalse(rendered.contains("<dt>Base commit</dt>"), rendered);
        assertTrue(rendered.contains(">b3f4ca2<"), rendered);
        assertFalse(rendered.contains(">b3f4ca20087f9066de4c340522ff84e0558e1ad1<"), rendered);
        assertTrue(rendered.contains("title=\"b3f4ca20087f9066de4c340522ff84e0558e1ad1\""), rendered);
        assertTrue(rendered.contains("Kaydedilmemiş değişiklik"), rendered);
    }

    @Test
    void headerShowsThreeSeparateCommitRowsWhenBaseMergeBaseAndHeadDiffer() throws java.io.IOException {
        VcsIdentity identity = new VcsIdentity("1111111111111111111111111111111111111a", "feature",
            "2222222222222222222222222222222222222b", "3333333333333333333333333333333333333c", false);
        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "base-ref", "all", identity,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), List.of(), List.of());

        String rendered = HtmlRenderer.render(doc);

        assertTrue(rendered.contains("<dt>Base commit</dt>"), rendered);
        assertTrue(rendered.contains("<dt>Merge-base</dt>"), rendered);
        assertTrue(rendered.contains("<dt>HEAD</dt>"), rendered);
        assertFalse(rendered.contains("<dt>Commit</dt>"), rendered);
    }

    @Test
    void changedFilesFindingsAndReasonsAreFilterableWithASearchInput() {
        ChangedFile file = new ChangedFile("src/main/java/Foo.java", "root", Classification.MAPPED, 5, 3, List.of());
        Finding finding = new Finding("NO_RECOGNIZED_ORACLE", Severity.WARNING, Confidence.HIGH, "root",
            "src/test/FooTest.java", 1, 1, "com.example.FooTest#m()", "no assertions here", "add one", "0123456789abcdef", null);
        AnalysisReason warning = new AnalysisReason("SOME_CODE", "a warning message");

        VerdictDocument doc = new VerdictDocument("0.1.0", "0.1.0-TEST", true, List.of(),
            17, "UTF-8", List.of(), List.of(module()), "uncommitted", "all", null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable("unavailable_no_vcs"),
            List.of(file), List.of(finding), List.of(warning));

        String rendered = HtmlRenderer.render(doc);

        assertTrue(rendered.contains("oninput=\"coverdictFilterRows(this)\""), rendered);
        assertTrue(rendered.contains("data-search=\"root src/main/java/foo.java mapped\""), rendered);
        assertTrue(rendered.contains("data-search=\"no_recognized_oracle warning high src/test/footest.java com.example.footest#m() no assertions here add one\""), rendered);
        assertTrue(rendered.contains("data-search=\"some_code a warning message\""), rendered);
        assertTrue(rendered.contains("class=\"filterable\""), rendered);
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
