package dev.coverdict.analysis.redundancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link SubsumptionAnalyzer} directly against hand-built {@link
 * KillMatrix.Data} - no PIT/mutation-collection machinery involved, per the
 * "pure logic gets isolated fixtures" split {@code MutationResultAccumulatorTest}
 * already establishes for L3.
 */
class SubsumptionAnalyzerTest {

    private static final String T1 = "com.example.T1";
    private static final String T2 = "com.example.T2";
    private static final String T3 = "com.example.T3";
    private static final String M1 = "m1";
    private static final String M2 = "m2";
    private static final String M3 = "m3";
    private static final String M4 = "m4";
    private static final String M5 = "m5";

    @Test
    void aTestWhoseKillSetIsAStrictSubsetOfAnotherIsReportedAsSubsumed() {
        // T1 kills {m1}; T2 kills {m1, m2} - T1's kill set is a strict subset of T2's.
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1), T2, Set.of(M1, M2)),
            Map.of(M1, Set.of(T1, T2), M2, Set.of(T2)));

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertEquals(1, results.size());
        SubsumptionAnalyzer.Subsumption result = results.get(0);
        assertEquals(T1, result.subsumedTest());
        assertEquals(T2, result.dominatorTest());
        assertEquals(1, result.subsumedKillCount());
        assertEquals(2, result.dominatorKillCount());
        assertFalse(result.ambiguousDominator());
    }

    @Test
    void twoTestsWithEqualKillSetsNeverSubsumeEachOther() {
        // Equal-size subset-superset is the same set, not a strict superset - neither dominates.
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1, M2), T2, Set.of(M1, M2)),
            Map.of(M1, Set.of(T1, T2), M2, Set.of(T1, T2)));

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertTrue(results.isEmpty());
    }

    @Test
    void aTestWithAnEmptyKillSetIsNeverReported() {
        // T1 kills nothing - the empty set is a subset of every set, so
        // without this exclusion T1 would be "subsumed" by everything.
        KillMatrix.Data matrix = matrix(
            Map.of(T2, Set.of(M1)),
            Map.of(M1, Set.of(T2)));
        // T1 never appears in killsByTest at all (no kills recorded) - the
        // realistic shape KillMatrix.build produces for a test with zero kills.

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertTrue(results.isEmpty());
    }

    @Test
    void anEssentialTestIsNeverReportedEvenIfItsKillSetLooksLikeASubset() {
        // T1 is the sole killer of m1 (essential) even though {m1} could
        // otherwise look "dominated" by a hypothetical richer test.
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1), T2, Set.of(M1, M2)),
            Map.of(M1, Set.of(T1), M2, Set.of(T2)));
        // Here m1's only killer is T1, so T1 is essential - the intersection
        // step would find candidates={T1} minus itself = empty anyway, but
        // the essential check is asserted explicitly by this scenario.

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertTrue(results.isEmpty());
    }

    @Test
    void pickTheNarrowestStrictSupersetWhenMultipleDominatorsQualify() {
        // T1 kills {m1}. T2 kills {m1, m2} and T3 kills {m1, m4, m5} - both
        // are strict supersets of T1's kill set (candidates for T1), but
        // T2 (size 2) is narrower than T3 (size 3), so T2 must win. Deliberately
        // NOT a superset chain (T3 does not contain m2) - T2 itself must stay
        // undominated, isolating "pick the narrowest among several qualifying
        // candidates" from the separate "domination can chain" behavior.
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1), T2, Set.of(M1, M2), T3, Set.of(M1, M4, M5)),
            Map.of(M1, Set.of(T1, T2, T3), M2, Set.of(T2), M4, Set.of(T3), M5, Set.of(T3)));

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertEquals(1, results.size());
        assertEquals(T1, results.get(0).subsumedTest());
        assertEquals(T2, results.get(0).dominatorTest());
        assertFalse(results.get(0).ambiguousDominator());
    }

    @Test
    void aDominationChainReportsEachSubsumedLinkAgainstItsOwnNarrowestDominator() {
        // T1 kills {m1}. T2 kills {m1, m2} (strictly richer than T1). T3
        // kills {m1, m2, m3} (strictly richer than both). Domination chains -
        // T1 is subsumed by T2, and T2 is itself subsumed by T3 (its kill set
        // is also a strict subset of T3's). Both are real, independent facts
        // about the kill matrix; T3 is untouched (nothing strictly contains it).
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1), T2, Set.of(M1, M2), T3, Set.of(M1, M2, M3)),
            Map.of(M1, Set.of(T1, T2, T3), M2, Set.of(T2, T3), M3, Set.of(T3)));

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertEquals(2, results.size());
        assertEquals(T1, results.get(0).subsumedTest());
        assertEquals(T2, results.get(0).dominatorTest());
        assertEquals(T2, results.get(1).subsumedTest());
        assertEquals(T3, results.get(1).dominatorTest());
    }

    @Test
    void tiedNarrowestDominatorsAreFlaggedAmbiguousAndResolvedLexicographically() {
        // T1 kills {m1}. T2 and T3 both kill exactly {m1, m2} (tied, both narrowest strict supersets).
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1), T2, Set.of(M1, M2), T3, Set.of(M1, M2)),
            Map.of(M1, Set.of(T1, T2, T3), M2, Set.of(T2, T3)));

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertEquals(1, results.size());
        SubsumptionAnalyzer.Subsumption result = results.get(0);
        assertEquals(T2, result.dominatorTest()); // T2 < T3 lexicographically
        assertTrue(result.ambiguousDominator());
    }

    @Test
    void disjointKillSetsNeverProduceASubsumption() {
        KillMatrix.Data matrix = matrix(
            Map.of(T1, Set.of(M1), T2, Set.of(M2)),
            Map.of(M1, Set.of(T1), M2, Set.of(T2)));

        List<SubsumptionAnalyzer.Subsumption> results = SubsumptionAnalyzer.analyze(matrix);

        assertTrue(results.isEmpty());
    }

    private static KillMatrix.Data matrix(Map<String, Set<String>> killsByTest, Map<String, Set<String>> testsByMutant) {
        return new KillMatrix.Data(Map.copyOf(killsByTest), Map.copyOf(testsByMutant));
    }
}
