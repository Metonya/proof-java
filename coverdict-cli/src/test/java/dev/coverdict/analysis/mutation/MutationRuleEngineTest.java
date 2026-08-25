package dev.coverdict.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
