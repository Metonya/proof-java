package dev.coverdict.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * Covers the non-spawning paths (no changed production code, no bound
 * classpath, an unreadable classpath list) - the same boundary {@code
 * PerTestCollector} leaves untested for the real-PIT-run path, which is
 * exercised by the {@code -Pmutation-it} profile instead.
 */
class MutationCollectorTest {

    @TempDir
    Path repoRoot;

    private static final ModuleDefinition MODULE =
        new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of("app/src/test/java"));

    @Test
    void aModuleWithNoMappedChangedFilesIsSkippedSilently() {
        ChangedFile unrelated = new ChangedFile("app/src/main/java/com/example/Other.java", "app",
            Classification.EXCLUDED, null, null, null);

        MutationCollector.Result result = MutationCollector.collect(repoRoot, List.of(MODULE),
            List.of(unrelated), Map.of(), Duration.ofMinutes(1));

        assertTrue(result.modules().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aChangedModuleWithNoBoundClasspathWarns() {
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());

        MutationCollector.Result result = MutationCollector.collect(repoRoot, List.of(MODULE),
            List.of(changed), Map.of(), Duration.ofMinutes(1));

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("MUTATION_CLASSPATH_MISSING", result.warnings().get(0).code());
    }

    @Test
    void aChangedModuleWithAnUnreadableClasspathFilePropagatesTheLoaderWarning() {
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());

        MutationCollector.Result result = MutationCollector.collect(repoRoot, List.of(MODULE),
            List.of(changed), Map.of("app", "missing-classpath.txt"), Duration.ofMinutes(1));

        assertTrue(result.modules().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("MUTATION_CLASSPATH_MISSING", result.warnings().get(0).code());
    }
}
