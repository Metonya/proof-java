package dev.coverdict.analysis.redundancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.mutation.MutatedMethod;
import dev.coverdict.analysis.mutation.Mutant;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;

class SubsumedTestRuleTest {

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
    void reportsTheNarrowerTestAsSubsumedByTheStrictlyRicherOne() {
        // NARROW_TEST kills {m1}; RICH_TEST kills {m1, m2} - a strict superset.
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutatedMethod m2 = new MutatedMethod("com.example.Calc", "subtract", "(II)I", 20, 20,
            List.of(new Mutant(MUTATOR, 20, "KILLED", List.of(RICH_TEST))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(m1, m2), List.of());

        List<Finding> findings = SubsumedTestRule.evaluate(repoRoot, MODULE, evidence);

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals("SUBSUMED_TEST", finding.rule());
        assertEquals(NARROW_TEST, finding.testMethod());
        assertEquals(RICH_TEST, finding.relatedTestMethod());
        assertEquals("app/src/test/java/com/example/CalcTest.java", finding.path());
        assertEquals("app/src/test/java/com/example/CalcTest.java", finding.relatedPath());
        assertEquals(4, finding.startLine()); // addsTwoNumbers() declaration line
        assertEquals(4, finding.endLine());
        assertNull(finding.productionMethod());
        assertTrue(finding.suggestedAction().toLowerCase().contains("review"));
        assertFalse(finding.suggestedAction().toLowerCase().contains("delete"));
    }

    @Test
    void confidenceIsMediumBelowTheHighConfidenceKillCountThreshold() {
        // Only 1 shared mutant - below the HIGH-confidence minimum of 3, even with a single unambiguous dominator.
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutatedMethod m2 = new MutatedMethod("com.example.Calc", "subtract", "(II)I", 20, 20,
            List.of(new Mutant(MUTATOR, 20, "KILLED", List.of(RICH_TEST))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(m1, m2), List.of());

        List<Finding> findings = SubsumedTestRule.evaluate(repoRoot, MODULE, evidence);

        assertEquals(Confidence.MEDIUM, findings.get(0).confidence());
    }

    @Test
    void confidenceIsHighAtOrAboveTheKillCountThresholdWithAnUnambiguousDominator() {
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutatedMethod m2 = new MutatedMethod("com.example.Calc", "subtract", "(II)I", 20, 20,
            List.of(new Mutant(MUTATOR, 20, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutatedMethod m3 = new MutatedMethod("com.example.Calc", "multiply", "(II)I", 30, 30,
            List.of(new Mutant(MUTATOR, 30, "KILLED", List.of(NARROW_TEST, RICH_TEST))));
        MutatedMethod m4 = new MutatedMethod("com.example.Calc", "divide", "(II)I", 40, 40,
            List.of(new Mutant(MUTATOR, 40, "KILLED", List.of(RICH_TEST))));
        MutationModuleEvidence evidence =
            new MutationModuleEvidence("app", List.of(m1, m2, m3, m4), List.of());

        List<Finding> findings = SubsumedTestRule.evaluate(repoRoot, MODULE, evidence);

        assertEquals(1, findings.size());
        assertEquals(Confidence.HIGH, findings.get(0).confidence());
    }

    @Test
    void skipsSilentlyWhenTheSubsumedTestCannotBeLocatedOnDisk() {
        String unresolvable = "com.example.GhostTest#neverWritten()";
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(unresolvable, RICH_TEST))));
        MutatedMethod m2 = new MutatedMethod("com.example.Calc", "subtract", "(II)I", 20, 20,
            List.of(new Mutant(MUTATOR, 20, "KILLED", List.of(RICH_TEST))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(m1, m2), List.of());

        List<Finding> findings = SubsumedTestRule.evaluate(repoRoot, MODULE, evidence);

        assertTrue(findings.isEmpty());
    }

    @Test
    void producesNoFindingsWhenNoTestIsStrictlySubsumed() {
        MutatedMethod m1 = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 10,
            List.of(new Mutant(MUTATOR, 10, "KILLED", List.of(NARROW_TEST))));
        MutationModuleEvidence evidence = new MutationModuleEvidence("app", List.of(m1), List.of());

        List<Finding> findings = SubsumedTestRule.evaluate(repoRoot, MODULE, evidence);

        assertTrue(findings.isEmpty());
    }
}
