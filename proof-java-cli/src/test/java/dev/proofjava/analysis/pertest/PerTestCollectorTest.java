package dev.proofjava.analysis.pertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * Covers the non-spawning paths (no changed production code, no bound
 * classpath, an unreadable classpath list) - {@link MutationCollectorTest}'s
 * L2 sibling. The real-PIT-run path is exercised end to end only by a live
 * {@code --per-test-report} dogfood run, not by a fast unit test.
 */
class PerTestCollectorTest {

    @TempDir
    Path repoRoot;

    private static final ModuleDefinition MODULE =
        new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of("app/src/test/java"));

    /**
     * D-64 reversed the original "skipped silently" contract. {@code
     * --per-test-report} was asked for explicitly, so a module that
     * contributed nothing is a fact the caller needs (hard rule 3a) - WTA's
     * first dogfood run had every module land here (its base ref was
     * {@code HEAD}, so nothing had changed) and the verdict explained none
     * of it, reporting only an empty {@code perTest} block.
     */
    @Test
    void aModuleWithNoMappedChangedFilesWarnsRatherThanSkippingSilently() {
        ChangedFile unrelated = new ChangedFile("app/src/main/java/com/example/Other.java", "app",
            Classification.EXCLUDED, null, null, null);

        PerTestCollector.Result result = PerTestCollector.collect(repoRoot, List.of(MODULE), List.of(unrelated), Map.of(),
            Duration.ofMinutes(1));

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_NO_CHANGED_TARGETS", result.warnings().get(0).code());
        assertEquals("app", result.warnings().get(0).module());
    }

    @Test
    void aChangedModuleWithNoBoundClasspathWarns() {
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());

        PerTestCollector.Result result = PerTestCollector.collect(repoRoot, List.of(MODULE), List.of(changed), Map.of(),
            Duration.ofMinutes(1));

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }

    @Test
    void aChangedModuleWithAnUnreadableClasspathFilePropagatesTheLoaderWarning() {
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());

        PerTestCollector.Result result = PerTestCollector.collect(repoRoot, List.of(MODULE), List.of(changed),
            Map.of("app", "missing-classpath.txt"), Duration.ofMinutes(1));

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }

    // --- collectForTargets (Faz 14a, --per-test-target) ---

    @Test
    void aModuleAbsentFromTargetGlobsIsSkippedWithNoWarningOfItsOwn() {
        // PerTestTargetResolver already explains an empty target list
        // (PER_TEST_TARGET_NOT_BOUND / PER_TEST_TARGET_UNRESOLVED) - collectForTargets must not add a second, less specific one.
        PerTestCollector.Result result = PerTestCollector.collectForTargets(repoRoot, List.of(MODULE),
            Map.of(), Map.of(), Duration.ofMinutes(1), dev.proofjava.analysis.subprocess.EvidenceDiagnostics.none());

        assertTrue(result.modules().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aTargetedModuleWithNoBoundClasspathWarnsTheSameWayAChangedModuleWould() {
        PerTestCollector.Result result = PerTestCollector.collectForTargets(repoRoot, List.of(MODULE),
            Map.of("app", List.of("com.example.Calc*")), Map.of(), Duration.ofMinutes(1),
            dev.proofjava.analysis.subprocess.EvidenceDiagnostics.none());

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }
}
