package dev.coverdict.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.ModuleDefinition;

class MutationRuleEngineTest {

    private static final String RETURNS_MUTATOR = "org.pitest.mutationtest.engine.gregor.mutators.returns.NullReturnValsMutator";

    @Test
    void resolvesEachEvidenceModuleAgainstItsOwnDeclaredModuleAndChangedFiles() {
        ModuleDefinition module = new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of());
        ChangedFile changed = new ChangedFile("app/src/main/java/com/example/Calc.java", "app",
            Classification.MAPPED, 5, 3, List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of())));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(method), List.of());

        MutationRuleEngine.Result result = MutationRuleEngine.evaluate(List.of(module), List.of(changed), List.of(evidence));

        assertEquals(1, result.findings().size());
        assertEquals("app/src/main/java/com/example/Calc.java", result.findings().get(0).path());
        assertTrue(result.warnings().isEmpty());
    }

    /** Plan.md Faz 2: the injectable overload {@code --mutation-target} needs, bypassing {@code ChangedClassTargets}/a diff entirely. */
    @Test
    void resolvesEachEvidenceModuleAgainstAnInjectedClassNameToPathIndex() {
        ModuleDefinition module = new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of())));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(method), List.of());
        Map<String, Map<String, String>> classNameToPathByModuleId =
            Map.of("app", Map.of("com.example.Calc", "app/src/main/java/com/example/Calc.java"));

        MutationRuleEngine.Result result = MutationRuleEngine.evaluate(List.of(module), classNameToPathByModuleId, List.of(evidence));

        assertEquals(1, result.findings().size());
        assertEquals("app/src/main/java/com/example/Calc.java", result.findings().get(0).path());
        assertTrue(result.warnings().isEmpty());
    }

    /** A module id present in the evidence but absent from the injected index skips its findings rather than guessing (hard rule 3a). */
    @Test
    void aModuleAbsentFromTheInjectedIndexSkipsItsFindings() {
        ModuleDefinition module = new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of())));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(method), List.of());

        MutationRuleEngine.Result result = MutationRuleEngine.evaluate(List.of(module), Map.of(), List.of(evidence));

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void skipsEvidenceForAModuleIdNotInTheDeclaredSet() {
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of())));
        MutationModuleEvidence orphaned = new MutationModuleEvidence("ghost-module", List.of(method), List.of());

        MutationRuleEngine.Result result = MutationRuleEngine.evaluate(List.of(), List.of(), List.of(orphaned));

        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
}
