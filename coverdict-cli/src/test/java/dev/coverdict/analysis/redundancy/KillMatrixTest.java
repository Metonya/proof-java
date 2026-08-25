package dev.coverdict.analysis.redundancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.coverdict.analysis.mutation.MutatedMethod;
import dev.coverdict.analysis.mutation.Mutant;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;

class KillMatrixTest {

    private static final String MUTATOR = "org.pitest.mutationtest.engine.gregor.mutators.returns.NullReturnValsMutator";
    private static final String TEST_A = "com.example.CalcTest#addsTwoNumbers()";
    private static final String TEST_B = "com.example.CalcTest#addsNegativeNumbers()";

    @Test
    void onlyKilledMutantsWithNonEmptyKillingTestsEnterEitherIndex() {
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 12, List.of(
            new Mutant(MUTATOR, 10, "SURVIVED", List.of()),
            new Mutant(MUTATOR, 11, "NO_COVERAGE", List.of()),
            new Mutant(MUTATOR, 12, "KILLED", List.of(TEST_A))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("m", List.of(method), List.of());

        KillMatrix.Data data = KillMatrix.build(evidence);

        assertEquals(1, data.killsByTest().size());
        assertTrue(data.killsByTest().containsKey(TEST_A));
        assertEquals(1, data.testsByMutant().size());
    }

    @Test
    void distinctMutantsAtTheSameMutatorAndLineGetDistinctIdentitiesViaOrdinal() {
        // Not confirmed against a live PIT run whether this ever really
        // happens (see TestIdentity/KillMatrix javadoc) - the ordinal
        // disambiguation must hold regardless.
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10, List.of(
            new Mutant(MUTATOR, 10, "KILLED", List.of(TEST_A)),
            new Mutant(MUTATOR, 10, "KILLED", List.of(TEST_B))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("m", List.of(method), List.of());

        KillMatrix.Data data = KillMatrix.build(evidence);

        assertEquals(2, data.testsByMutant().size());
        assertEquals(1, data.killsByTest().get(TEST_A).size());
        assertEquals(1, data.killsByTest().get(TEST_B).size());
        assertNotEquals(data.killsByTest().get(TEST_A), data.killsByTest().get(TEST_B),
            "each test's single mutant kill should be a distinct synthetic mutant id");
    }

    @Test
    void multipleTestsKillingTheSameMutantAllAppearAsItsKillers() {
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10, List.of(
            new Mutant(MUTATOR, 10, "KILLED", List.of(TEST_A, TEST_B))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("m", List.of(method), List.of());

        KillMatrix.Data data = KillMatrix.build(evidence);

        String mutantId = data.testsByMutant().keySet().iterator().next();
        assertEquals(Set.of(TEST_A, TEST_B), data.testsByMutant().get(mutantId));
        assertTrue(data.killsByTest().get(TEST_A).contains(mutantId));
        assertTrue(data.killsByTest().get(TEST_B).contains(mutantId));
    }

    @Test
    void anEmptyEvidenceProducesEmptyIndexes() {
        MutationModuleEvidence evidence = new MutationModuleEvidence("m", List.of(), List.of());

        KillMatrix.Data data = KillMatrix.build(evidence);

        assertTrue(data.killsByTest().isEmpty());
        assertTrue(data.testsByMutant().isEmpty());
    }
}
