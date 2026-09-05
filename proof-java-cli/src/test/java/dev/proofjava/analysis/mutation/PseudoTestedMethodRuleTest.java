package dev.proofjava.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.proofjava.analysis.model.Confidence;

class PseudoTestedMethodRuleTest {

    private static final Map<String, String> CLASS_NAME_TO_PATH =
        Map.of("com.example.Calc", "src/main/java/com/example/Calc.java");

    private static final String RETURNS_MUTATOR = "org.pitest.mutationtest.engine.gregor.mutators.returns.NullReturnValsMutator";
    private static final String VOID_METHOD_CALL_MUTATOR = "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator";

    @Test
    void firesWithHighConfidenceWhenAllMutantsAreReturnsAndAllSurvived() {
        MutatedMethod method = method(List.of(
            new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of()),
            new Mutant(RETURNS_MUTATOR, 11, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertEquals(1, result.findings().size());
        var finding = result.findings().get(0);
        assertEquals("PSEUDO_TESTED_METHOD", finding.rule());
        assertEquals(Confidence.HIGH, finding.confidence());
        assertEquals("src/main/java/com/example/Calc.java", finding.path());
        assertEquals(10, finding.startLine());
        assertEquals(11, finding.endLine());
        assertEquals("com.example.Calc#add(II)I", finding.productionMethod());
        assertEquals(null, finding.testMethod());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void dropsToMediumConfidenceWhenAVoidMethodCallMutatorIsMixedIn() {
        MutatedMethod method = method(List.of(
            new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of()),
            new Mutant(VOID_METHOD_CALL_MUTATOR, 11, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertEquals(Confidence.MEDIUM, result.findings().get(0).confidence());
    }

    @Test
    void staysSilentWhenAtLeastOneMutantWasKilled() {
        MutatedMethod method = method(List.of(
            new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of()),
            new Mutant(RETURNS_MUTATOR, 11, "KILLED", List.of("com.example.CalcTest#addsTwoNumbers()"))));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void staysSilentAndWarnsNothingWhenAMutantHasNoCoverage() {
        MutatedMethod method = method(List.of(
            new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of()),
            new Mutant(RETURNS_MUTATOR, 11, "NO_COVERAGE", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void warnsInsteadOfGuessingWhenAMutantHasAnInconclusiveStatus() {
        MutatedMethod method = method(List.of(
            new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of()),
            new Mutant(RETURNS_MUTATOR, 11, "TIMED_OUT", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("MUTATION_INCONCLUSIVE_STATUS", result.warnings().get(0).code());
    }

    @Test
    void ignoresAMethodWithNoMutants() {
        MutatedMethod method = method(List.of());

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void stripsNestedClassSuffixBeforeResolvingThePath() {
        MutatedMethod nested = new MutatedMethod("com.example.Calc$Inner", "add", "(II)I", 10, 10,
            List.of(new Mutant(RETURNS_MUTATOR, 10, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(nested), List.of()));

        assertEquals(1, result.findings().size());
        assertEquals("src/main/java/com/example/Calc.java", result.findings().get(0).path());
    }

    @Test
    void skipsSilentlyWhenTheClassCannotBeResolvedToAPath() {
        MutatedMethod method = new MutatedMethod("com.example.Unmapped", "m", "()V", 1, 1,
            List.of(new Mutant(RETURNS_MUTATOR, 1, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(method), List.of()));

        assertTrue(result.findings().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    /**
     * D-87: a surviving mutant of hashCode proves nothing. Its contract only
     * requires equal objects to agree, so {@code return 0;} is a legal hashCode
     * and passes the {@code a.hashCode() == b.hashCode()} assertion a good
     * hashCode test makes. Reported against google/gson's
     * {@code JsonArray#hashCode}, which is tested.
     */
    @Test
    void neverFiresOnHashCodeBecauseAConstantMutantIsALegalImplementation() {
        MutatedMethod hashCode = new MutatedMethod("com.example.Calc", "hashCode", "()I", 20, 22,
            List.of(new Mutant(RETURNS_MUTATOR, 20, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(hashCode), List.of()));

        assertTrue(result.findings().isEmpty(), result.findings().toString());
        assertTrue(result.warnings().isEmpty(), "silence here is the rule declining to speak, not a warning");
    }

    @Test
    void neverFiresOnToStringForTheSameReason() {
        MutatedMethod toString = new MutatedMethod("com.example.Calc", "toString", "()Ljava/lang/String;", 30, 32,
            List.of(new Mutant(RETURNS_MUTATOR, 30, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(toString), List.of()));

        assertTrue(result.findings().isEmpty(), result.findings().toString());
    }

    /**
     * equals is deliberately not exempt: its contract is real, so a
     * constant-returning mutant fails any test that compares two unequal
     * objects, and a surviving one is genuine evidence.
     */
    @Test
    void stillFiresOnEqualsWhoseContractAMutantCanActuallyViolate() {
        MutatedMethod equals = new MutatedMethod("com.example.Calc", "equals", "(Ljava/lang/Object;)Z", 40, 42,
            List.of(new Mutant(RETURNS_MUTATOR, 40, "SURVIVED", List.of())));

        PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate("m", CLASS_NAME_TO_PATH,
            new MutationModuleEvidence("m", List.of(equals), List.of()));

        assertEquals(1, result.findings().size());
    }

    private static MutatedMethod method(List<Mutant> mutants) {
        return new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 11, mutants);
    }
}
