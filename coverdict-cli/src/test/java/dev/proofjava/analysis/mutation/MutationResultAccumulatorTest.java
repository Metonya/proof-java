package dev.proofjava.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;

class MutationResultAccumulatorTest {

    @Test
    void groupsMutantsByClassMethodAndDescriptor() {
        MutationResult killed = mutationResult("com/example/Calc", "add", "(II)I", 10, "RETURNS",
            DetectionStatus.KILLED, "com.example.CalcTest#addsTwoNumbers");
        MutationResult survived = mutationResult("com/example/Calc", "add", "(II)I", 12, "RETURNS",
            DetectionStatus.SURVIVED, null);
        ClassMutationResults classResults = new ClassMutationResults(List.of(killed, survived));

        MutationModuleEvidence evidence = MutationResultAccumulator.accumulate("m", List.of(classResults));

        assertEquals("m", evidence.moduleId());
        assertEquals(1, evidence.methods().size());
        MutatedMethod method = evidence.methods().get(0);
        assertEquals("com.example.Calc", method.className());
        assertEquals("add", method.methodName());
        assertEquals("(II)I", method.methodDescription());
        assertEquals(10, method.firstLine());
        assertEquals(12, method.lastLine());
        assertEquals(2, method.mutants().size());
        assertTrue(evidence.warnings().isEmpty());
    }

    @Test
    void separatesOverloadsByMethodDescriptor() {
        MutationResult intOverload = mutationResult("com/example/Calc", "add", "(II)I", 10, "RETURNS",
            DetectionStatus.SURVIVED, null);
        MutationResult doubleOverload = mutationResult("com/example/Calc", "add", "(DD)D", 20, "RETURNS",
            DetectionStatus.SURVIVED, null);
        ClassMutationResults classResults = new ClassMutationResults(List.of(intOverload, doubleOverload));

        MutationModuleEvidence evidence = MutationResultAccumulator.accumulate("m", List.of(classResults));

        assertEquals(2, evidence.methods().size());
    }

    @Test
    void carriesTheFullKillingTestSet() {
        MutationResult result = mutationResultWithKillingTests("com/example/Calc", "add", "(II)I", 10, "RETURNS",
            DetectionStatus.KILLED, List.of("com.example.ATest#m", "com.example.BTest#m"));
        ClassMutationResults classResults = new ClassMutationResults(List.of(result));

        MutationModuleEvidence evidence = MutationResultAccumulator.accumulate("m", List.of(classResults));

        List<String> killingTests = evidence.methods().get(0).mutants().get(0).killingTests();
        assertEquals(List.of("com.example.ATest#m", "com.example.BTest#m"), killingTests);
    }

    @Test
    void truncatesAndWarnsPastTheRecordLimit() {
        List<MutationResult> many = new java.util.ArrayList<>();
        for (int i = 0; i < MutationResultAccumulator.MAX_MUTANT_RECORDS + 1; i++) {
            many.add(mutationResult("com/example/Calc", "m" + i, "()V", 1, "RETURNS", DetectionStatus.SURVIVED, null));
        }
        ClassMutationResults classResults = new ClassMutationResults(many);

        MutationModuleEvidence evidence = MutationResultAccumulator.accumulate("m", List.of(classResults));

        assertTrue(evidence.methods().isEmpty());
        assertEquals(1, evidence.warnings().size());
        assertEquals("MUTATION_TRUNCATED", evidence.warnings().get(0).code());
        assertEquals("m", evidence.warnings().get(0).module());
    }

    private static MutationResult mutationResult(String internalClassName, String methodName, String desc,
                                                   int line, String mutator, DetectionStatus status,
                                                   String killingTest) {
        List<String> killingTests = killingTest == null ? List.of() : List.of(killingTest);
        return mutationResultWithKillingTests(internalClassName, methodName, desc, line, mutator, status, killingTests);
    }

    private static MutationResult mutationResultWithKillingTests(String internalClassName, String methodName,
                                                                   String desc, int line, String mutator,
                                                                   DetectionStatus status, List<String> killingTests) {
        Location location = new Location(ClassName.fromString(internalClassName), methodName, desc);
        MutationIdentifier id = new MutationIdentifier(location, 0, mutator);
        MutationDetails details = new MutationDetails(id, internalClassName + ".java", "a mutant", line, 0);
        MutationStatusTestPair statusPair = new MutationStatusTestPair(killingTests.size(), status, killingTests, List.of());
        return new MutationResult(details, statusPair);
    }
}
