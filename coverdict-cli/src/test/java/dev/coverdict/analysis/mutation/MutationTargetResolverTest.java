package dev.coverdict.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.ModuleDefinition;

class MutationTargetResolverTest {

    @TempDir
    Path repoRoot;

    private static final ModuleDefinition MODULE =
        new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of());

    @Test
    void aResolvedTargetProducesAGlobAndAClassNameToPathEntry() throws IOException {
        Files.createDirectories(repoRoot.resolve("app/src/main/java/com/example"));
        Files.createFile(repoRoot.resolve("app/src/main/java/com/example/Calc.java"));

        MutationTargetResolver.Result result = MutationTargetResolver.resolve(repoRoot, List.of(MODULE),
            Map.of("app", List.of("com.example.Calc")));

        assertEquals(List.of("com.example.Calc*"), result.targetGlobsById().get("app"));
        assertEquals("app/src/main/java/com/example/Calc.java", result.classNameToPathByModuleId().get("app").get("com.example.Calc"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void anUnresolvedTargetWarnsAndIsExcludedFromTheGlobList() {
        MutationTargetResolver.Result result = MutationTargetResolver.resolve(repoRoot, List.of(MODULE),
            Map.of("app", List.of("com.example.DoesNotExist")));

        assertFalse(result.targetGlobsById().containsKey("app"));
        assertEquals(1, result.warnings().size());
        assertEquals("MUTATION_TARGET_UNRESOLVED", result.warnings().get(0).code());
        assertEquals("app", result.warnings().get(0).module());
    }

    @Test
    void aModuleWithNoMutationTargetAtAllGetsItsOwnDistinctWarning() {
        MutationTargetResolver.Result result = MutationTargetResolver.resolve(repoRoot, List.of(MODULE), Map.of());

        assertFalse(result.targetGlobsById().containsKey("app"));
        assertEquals(1, result.warnings().size());
        assertEquals("MUTATION_TARGET_NOT_BOUND", result.warnings().get(0).code());
    }

    @Test
    void oneResolvedAndOneUnresolvedTargetInTheSameModuleKeepsOnlyTheResolvedOneAndWarnsForTheOther() throws IOException {
        Files.createDirectories(repoRoot.resolve("app/src/main/java/com/example"));
        Files.createFile(repoRoot.resolve("app/src/main/java/com/example/Calc.java"));

        MutationTargetResolver.Result result = MutationTargetResolver.resolve(repoRoot, List.of(MODULE),
            Map.of("app", List.of("com.example.Calc", "com.example.Missing")));

        assertEquals(List.of("com.example.Calc*"), result.targetGlobsById().get("app"));
        assertEquals(1, result.warnings().size());
        assertEquals("MUTATION_TARGET_UNRESOLVED", result.warnings().get(0).code());
        assertTrue(result.warnings().get(0).message().contains("com.example.Missing"), result.warnings().get(0).message());
    }
}
