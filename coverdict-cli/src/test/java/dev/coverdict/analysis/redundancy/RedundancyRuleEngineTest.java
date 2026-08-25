package dev.coverdict.analysis.redundancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.mutation.MutatedMethod;
import dev.coverdict.analysis.mutation.Mutant;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;

class RedundancyRuleEngineTest {

    private static final String MUTATOR = "org.pitest.mutationtest.engine.gregor.mutators.returns.NullReturnValsMutator";
    private static final String NARROW_TEST = "com.example.CalcTest#addsTwoNumbers()";
    private static final String RICH_TEST = "com.example.CalcTest#addsSeveralCombinations()";

    private static final ModuleDefinition MODULE =
        new ModuleDefinition("app", "app", List.of("app/src/main/java"), List.of("app/src/test/java"));

    @TempDir
    Path repoRoot;

    @BeforeEach
    void writeTestSource() throws IOException {
        Path testFile = repoRoot.resolve("app/src/test/java/com/example/CalcTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
            package com.example;

            class CalcTest {
                void addsTwoNumbers() {
                }

                void addsSeveralCombinations() {
                }
            }
            """);
    }

    @Test
    void resolvesEachEvidenceModuleAgainstItsOwnDeclaredModule() {
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutatedMethod m2 = new MutatedMethod("com.example.Calc", "subtract", "(II)I", 20, 20,
            List.of(new Mutant(MUTATOR, 20, "KILLED", List.of(RICH_TEST))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(m1, m2), List.of());

        RedundancyRuleEngine.Result result = RedundancyRuleEngine.evaluate(repoRoot, List.of(MODULE), List.of(evidence));

        assertEquals(1, result.findings().size());
        assertEquals("SUBSUMED_TEST", result.findings().get(0).rule());
    }

    @Test
    void skipsEvidenceForAModuleIdNotInTheDeclaredSet() {
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutationModuleEvidence orphaned = new MutationModuleEvidence("ghost-module", List.of(m1), List.of());

        RedundancyRuleEngine.Result result = RedundancyRuleEngine.evaluate(repoRoot, List.of(), List.of(orphaned));

        assertTrue(result.findings().isEmpty());
    }
}
