package dev.coverdict.analysis.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import dev.coverdict.analysis.jacoco.LineCoverage;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.LineRange;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.ResolvedSourceFile;

/**
 * One test per row of the classification table in {@link ChangedFileClassifier}'s
 * own javadoc, plus the two aggregate warnings and the untracked-file rules
 * (D-16, D-27). Every {@link ResolvedSourceFile} here stands in for what
 * {@link ModuleBinder} would have already produced from a real JaCoCo
 * report - this class is tested without any XML or git involved.
 */
class ChangedFileClassifierTest {

    private static final ModuleDefinition ROOT = new ModuleDefinition("root", ".",
        List.of("src/main/java"), List.of("src/test/java"));

    @Test
    void nonJvmChangedPathIsIgnoredEntirely() {
        ClassificationResult result = classify(changed("README.md", 1, 2, 3), List.of(ROOT), List.of(), List.of());
        assertTrue(result.changedFiles().isEmpty());
        assertTrue(result.incompleteReasons().isEmpty());
    }

    @Test
    void pathOutsideEveryModuleRootIsUnknownWithNoModuleAndGoesIncomplete() {
        ModuleDefinition sub = new ModuleDefinition("sub", "modules/sub",
            List.of("modules/sub/src/main/java"), List.of("modules/sub/src/test/java"));

        ClassificationResult result = classify(
            changed("other/Outside.java", 1), List.of(sub), List.of(), List.of());

        assertEquals(1, result.changedFiles().size());
        ChangedFile file = result.changedFiles().get(0);
        assertEquals(Classification.UNKNOWN, file.classification());
        assertNull(file.module());
        assertEquals(1, result.incompleteReasons().size());
        assertEquals("CHANGED_JAVA_OUTSIDE_MODULES", result.incompleteReasons().get(0).code());
    }

    @Test
    void nestedModuleRootsResolveToTheLongestMatchingRoot() {
        ModuleDefinition parent = new ModuleDefinition("parent", ".",
            List.of("src/main/java"), List.of("src/test/java"));
        ModuleDefinition child = new ModuleDefinition("child", "modules/sub",
            List.of("modules/sub/src/main/java"), List.of("modules/sub/src/test/java"));
        // Declaration order is parent-then-child; longest-root-wins must not depend on that order.
        String path = "modules/sub/src/main/java/com/example/Deep.java";

        ClassificationResult result = classify(changed(path, 1), List.of(parent, child), List.of(), List.of());

        assertEquals("child", result.changedFiles().get(0).module());
    }

    @Test
    void kotlinFileUnderAModuleIsUnsupportedButKeepsItsModule() {
        ClassificationResult result = classify(
            changed("src/main/java/com/example/Extra.kt", 1), List.of(ROOT), List.of(), List.of());

        ChangedFile file = result.changedFiles().get(0);
        assertEquals(Classification.UNSUPPORTED, file.classification());
        assertEquals("root", file.module());
        assertTrue(result.incompleteReasons().isEmpty());
    }

    @Test
    void changedFileMatchingCoverageExclusionsIsExcluded() {
        ClassificationResult result = classify(
            changed("src/main/java/com/example/Generated.java", 1),
            List.of(ROOT), List.of("**/Generated.java"), List.of());

        assertEquals(Classification.EXCLUDED, result.changedFiles().get(0).classification());
    }

    @Test
    void changedFileUnderTestRootIsExcludedNotIncomplete() {
        ClassificationResult result = classify(
            changed("src/test/java/com/example/CalcTest.java", 1),
            List.of(ROOT), List.of(), List.of());

        assertEquals(Classification.EXCLUDED, result.changedFiles().get(0).classification());
        assertTrue(result.incompleteReasons().isEmpty(), "a changed test file is not missing evidence");
    }

    @Test
    void mappedFileComputesNewLinesCoveredNewLinesAndCoalescedUncoveredRanges() {
        // Report knows lines 1-8; ci>0 on 1,2,4,6,7 (covered), ci==0 on 3,5,8 (uncovered).
        ResolvedSourceFile bound = resolvedFile("src/main/java/com/example/Calc.java",
            covered(1), covered(2), uncovered(3), covered(4), uncovered(5), covered(6), covered(7), uncovered(8));
        // Changed (diff) lines: 3,4,5,6,7,8 - a subset of what the report knows.
        ClassificationResult result = classify(
            changed("src/main/java/com/example/Calc.java", 3, 4, 5, 6, 7, 8),
            List.of(ROOT), List.of(), List.of(bound));

        ChangedFile file = result.changedFiles().get(0);
        assertEquals(Classification.MAPPED, file.classification());
        assertEquals(6, file.newLines());
        assertEquals(3, file.coveredNewLines()); // 4, 6, 7
        assertEquals(List.of(new LineRange(3, 3), new LineRange(5, 5), new LineRange(8, 8)), file.uncoveredNewRanges());
        assertTrue(result.warnings().isEmpty(), "every changed line was present in the report - no staleness to warn about");
    }

    @Test
    void uncoveredNewRangesCoalescesConsecutiveLineNumbers() {
        ResolvedSourceFile bound = resolvedFile("src/main/java/com/example/Calc.java",
            uncovered(5), uncovered(6), covered(7), uncovered(8), uncovered(9), uncovered(10));

        ClassificationResult result = classify(
            changed("src/main/java/com/example/Calc.java", 5, 6, 7, 8, 9, 10),
            List.of(ROOT), List.of(), List.of(bound));

        assertEquals(List.of(new LineRange(5, 6), new LineRange(8, 10)),
            result.changedFiles().get(0).uncoveredNewRanges());
    }

    @Test
    void mappedFileWithNoExecutableLinesIsNonExecutable() {
        ResolvedSourceFile bound = resolvedFile("src/main/java/com/example/Empty.java");

        ClassificationResult result = classify(
            changed("src/main/java/com/example/Empty.java", 1), List.of(ROOT), List.of(), List.of(bound));

        assertEquals(Classification.NON_EXECUTABLE, result.changedFiles().get(0).classification());
    }

    @Test
    void packageInfoAbsentFromAnyReportIsNonExecutableNotIncomplete() {
        ClassificationResult result = classify(
            changed("src/main/java/com/example/package-info.java", 1), List.of(ROOT), List.of(), List.of());

        assertEquals(Classification.NON_EXECUTABLE, result.changedFiles().get(0).classification());
        assertTrue(result.incompleteReasons().isEmpty());
    }

    @Test
    void moduleInfoPresentInAReportIsMappedNotNonExecutable() {
        // module-info.java DOES sometimes appear in a real report - the report lookup must win over the name-based rule.
        ResolvedSourceFile bound = resolvedFile("src/main/java/module-info.java", covered(1));

        ClassificationResult result = classify(
            changed("src/main/java/module-info.java", 1), List.of(ROOT), List.of(), List.of(bound));

        assertEquals(Classification.MAPPED, result.changedFiles().get(0).classification());
    }

    @Test
    void javaFileWithNoReportEntryAndNoNameExcuseIsUnknownAndIncomplete() {
        ClassificationResult result = classify(
            changed("src/main/java/com/example/Stale.java", 1), List.of(ROOT), List.of(), List.of());

        ChangedFile file = result.changedFiles().get(0);
        assertEquals(Classification.UNKNOWN, file.classification());
        assertEquals("root", file.module());
        assertEquals(1, result.incompleteReasons().size());
        assertEquals("REPORT_MISSING_CHANGED_FILE", result.incompleteReasons().get(0).code());
    }

    @Test
    void changedLinesAbsentFromAnOtherwiseMappedReportAreCountedAndWarnedNotSilentlyDropped() {
        // Report only knows lines 1-3; the diff also touches lines 4 and 5 (new method the report predates).
        ResolvedSourceFile bound = resolvedFile("src/main/java/com/example/Calc.java",
            covered(1), covered(2), uncovered(3));

        ClassificationResult result = classify(
            changed("src/main/java/com/example/Calc.java", 1, 2, 3, 4, 5),
            List.of(ROOT), List.of(), List.of(bound));

        ChangedFile file = result.changedFiles().get(0);
        assertEquals(3, file.newLines(), "only the 3 lines the report actually knows about count toward the numbers");
        assertEquals(1, result.warnings().size());
        assertEquals("CHANGED_LINES_ABSENT_FROM_REPORT", result.warnings().get(0).code());
        assertTrue(result.warnings().get(0).message().contains("2"), "message should name the 2 missing lines: " + result.warnings().get(0).message());
        assertTrue(result.incompleteReasons().isEmpty(), "a partially-stale mapped file does not make the whole run incomplete");
    }

    @Test
    void multipleExcludedFilesProduceOneAggregateWarningNotOnePerFile() {
        Map<String, SortedSet<Integer>> changed = new LinkedHashMap<>();
        changed.put("src/main/java/com/example/GenA.java", lines(1));
        changed.put("src/main/java/com/example/GenB.java", lines(1));

        ClassificationResult result = ChangedFileClassifier.classify(
            changed, List.of(), List.of(ROOT), List.of("**/Gen*.java"), List.of());

        assertEquals(1, result.warnings().size());
        assertEquals("CHANGED_FILES_EXCLUDED", result.warnings().get(0).code());
        assertTrue(result.warnings().get(0).message().contains("2"), result.warnings().get(0).message());
    }

    @Test
    void untrackedJavaFileNotATestAndNotExcludedIsIncomplete() {
        ClassificationResult result = ChangedFileClassifier.classify(
            Map.of(), List.of("src/main/java/com/example/New.java"), List.of(ROOT), List.of(), List.of());

        assertEquals(1, result.incompleteReasons().size());
        assertEquals("UNTRACKED_JAVA_FILE", result.incompleteReasons().get(0).code());
    }

    @Test
    void untrackedJavaTestFileIsNotFlaggedAtAll() {
        ClassificationResult result = ChangedFileClassifier.classify(
            Map.of(), List.of("src/test/java/com/example/NewTest.java"), List.of(ROOT), List.of(), List.of());

        assertTrue(result.incompleteReasons().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void untrackedNonJavaFileIsJustAWarning() {
        ClassificationResult result = ChangedFileClassifier.classify(
            Map.of(), List.of("notes.txt"), List.of(ROOT), List.of(), List.of());

        assertTrue(result.incompleteReasons().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("UNTRACKED_NON_JAVA_FILE", result.warnings().get(0).code());
        assertEquals("notes.txt", result.warnings().get(0).path());
    }

    @Test
    void changedFilesAreSortedByModuleThenPath() {
        Map<String, SortedSet<Integer>> changed = new LinkedHashMap<>();
        changed.put("src/main/java/com/example/Zeta.java", lines(1));
        changed.put("src/main/java/com/example/Alpha.java", lines(1));

        ClassificationResult result = ChangedFileClassifier.classify(changed, List.of(), List.of(ROOT), List.of(), List.of());

        assertEquals(List.of(
            "src/main/java/com/example/Alpha.java",
            "src/main/java/com/example/Zeta.java"
        ), result.changedFiles().stream().map(ChangedFile::path).toList());
    }

    // --- fixtures ---

    private static ClassificationResult classify(Map<String, SortedSet<Integer>> changedLinesByPath,
                                                   List<ModuleDefinition> modules, List<String> exclusions,
                                                   List<ResolvedSourceFile> boundFiles) {
        return ChangedFileClassifier.classify(changedLinesByPath, List.of(), modules, exclusions, boundFiles);
    }

    private static Map<String, SortedSet<Integer>> changed(String path, int... lineNumbers) {
        Map<String, SortedSet<Integer>> map = new LinkedHashMap<>();
        map.put(path, lines(lineNumbers));
        return map;
    }

    private static SortedSet<Integer> lines(int... lineNumbers) {
        SortedSet<Integer> set = new TreeSet<>();
        for (int n : lineNumbers) {
            set.add(n);
        }
        return set;
    }

    private static ResolvedSourceFile resolvedFile(String path, LineCoverage... lines) {
        return new ResolvedSourceFile("root", path, List.of(lines), 0, 0, true);
    }

    private static LineCoverage covered(int number) {
        return new LineCoverage(number, 0, 1, 0, 0);
    }

    private static LineCoverage uncovered(int number) {
        return new LineCoverage(number, 1, 0, 0, 0);
    }
}
