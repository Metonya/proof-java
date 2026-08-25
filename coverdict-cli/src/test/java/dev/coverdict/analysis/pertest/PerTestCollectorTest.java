package dev.coverdict.analysis.pertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.ModuleDefinition;

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

    @Test
    void aModuleWithNoMappedChangedFilesIsSkippedSilently() {
        ChangedFile unrelated = new ChangedFile("app/src/main/java/com/example/Other.java", "app",
            Classification.EXCLUDED, null, null, null);

        PerTestCollector.Result result = PerTestCollector.collect(repoRoot, List.of(MODULE), List.of(unrelated), Map.of());

        assertTrue(result.modules().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aChangedModuleWithNoBoundClasspathWarns() {
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());

        PerTestCollector.Result result = PerTestCollector.collect(repoRoot, List.of(MODULE), List.of(changed), Map.of());

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }

    @Test
    void aChangedModuleWithAnUnreadableClasspathFilePropagatesTheLoaderWarning() {
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());

        PerTestCollector.Result result = PerTestCollector.collect(repoRoot, List.of(MODULE), List.of(changed),
            Map.of("app", "missing-classpath.txt"));

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }
}
