package dev.coverdict.analysis.redundancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * D-61's pure core: for each test with a non-empty kill set, is there another
 * test whose kill set is a strict superset? {@code
 * dominators(t) = (intersection over m in kills(t) of testsByMutant(m)) \ {t}}
 * already equals exactly {@code {u : kills(t) subsetOf kills(u), u != t}} -
 * no separate subset check needed, and no pairwise loop over every test pair
 * (O(matrix size), not O(tests^2)). Filtering that set to {@code
 * kills(u).size() > kills(t).size()} then keeps only the *strict* supersets
 * (an equal-size subset-superset pair is the same set).
 *
 * <p>Two exclusions apply before a test is even considered, both load-bearing
 * for D-61's "never flood the suite" ground rule, not incidental:
 * <ul>
 *   <li>{@code kills(t)} empty - the empty set is a subset of every set, so
 *       without this exclusion a test with zero mutation evidence would be
 *       reported as subsumed by every other test in the module.</li>
 *   <li>{@code t} is essential (some mutant's only killer) - mathematically
 *       impossible to be dominated once excluded is unnecessary work, but
 *       checked explicitly so the exclusion is visible in code, not just an
 *       emergent property of the math.</li>
 * </ul>
 *
 * <p>Each test is evaluated independently against the whole matrix, so a
 * domination chain (kills(A) &#8834; kills(B) &#8834; kills(C)) reports two findings,
 * not one - A is subsumed by B, and B is itself subsumed by C (a true,
 * independent fact about B's own kill set). This is not deduplicated: each
 * finding is a correct, separately actionable claim about its own anchor
 * test. A long chain in one module is exactly the "large fraction of the
 * suite flagged at once" scenario the firing-rate kill criterion (D-61)
 * watches for.
 */
public final class SubsumptionAnalyzer {

    private SubsumptionAnalyzer() {
    }

    public static List<Subsumption> analyze(KillMatrix.Data matrix) {
        Set<String> essential = essentialTests(matrix.testsByMutant());
        List<Subsumption> results = new ArrayList<>();

        for (String test : matrix.testsWithKills()) {
            Set<String> kills = matrix.killsByTest().get(test);
            if (kills.isEmpty() || essential.contains(test)) {
                continue;
            }
            Subsumption result = dominatorFor(matrix, test, kills);
            if (result != null) {
                results.add(result);
            }
        }
        results.sort(java.util.Comparator.comparing(Subsumption::subsumedTest));
        return List.copyOf(results);
    }

    private static Subsumption dominatorFor(KillMatrix.Data matrix, String test, Set<String> kills) {
        Set<String> candidates = intersectKillers(matrix, kills);
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.remove(test);
        return pickDominator(matrix, test, kills, candidates);
    }

    /**
     * The intersection, over every mutant {@code test} killed, of that
     * mutant's killer set - always a non-null set built from {@code kills}'
     * first element, never a null-initialized accumulator (SonarQube
     * java:S2259: the caller guarantees {@code kills} is non-empty, but the
     * type itself should not depend on that invariant), and never null on
     * return either (SonarQube java:S1168: empty means "no candidate", not
     * "no result" - the caller checks {@link Set#isEmpty()}).
     *
     * @return an empty set the moment the running intersection collapses to
     *         empty - no candidate can dominate {@code test} once that
     *         happens, so later mutants are never even examined.
     */
    private static Set<String> intersectKillers(KillMatrix.Data matrix, Set<String> kills) {
        Set<String> candidates = new TreeSet<>();
        boolean first = true;
        for (String mutantId : kills) {
            Set<String> killers = matrix.testsByMutant().get(mutantId);
            if (first) {
                candidates.addAll(killers);
                first = false;
            } else {
                candidates.retainAll(killers);
            }
            if (candidates.isEmpty()) {
                return candidates;
            }
        }
        return candidates;
    }

    /** Narrowest strict-superset candidate wins; a tie is resolved lexicographically and flagged via {@link Subsumption#ambiguousDominator()}. */
    private static Subsumption pickDominator(KillMatrix.Data matrix, String test, Set<String> kills, Set<String> candidates) {
        String best = null;
        int bestSize = Integer.MAX_VALUE;
        int tieCount = 0;
        for (String candidate : candidates) {
            int size = matrix.killsByTest().getOrDefault(candidate, Set.of()).size();
            if (size <= kills.size()) {
                continue; // not a strict superset - equal-size means an equal set, smaller is impossible after the intersection
            }
            if (size < bestSize) {
                best = candidate;
                bestSize = size;
                tieCount = 1;
            } else if (size == bestSize) {
                tieCount++;
                if (candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
        }
        if (best == null) {
            return null;
        }
        return new Subsumption(test, kills.size(), best, bestSize, tieCount > 1);
    }

    private static Set<String> essentialTests(Map<String, Set<String>> testsByMutant) {
        Set<String> essential = new TreeSet<>();
        for (Set<String> killers : testsByMutant.values()) {
            if (killers.size() == 1) {
                essential.add(killers.iterator().next());
            }
        }
        return essential;
    }

    /**
     * @param subsumedTest       the redundant test (this finding's anchor)
     * @param subsumedKillCount  how many mutants {@code subsumedTest} killed
     * @param dominatorTest      the narrowest test whose kill set is a strict superset of {@code subsumedTest}'s
     * @param dominatorKillCount how many mutants {@code dominatorTest} killed
     * @param ambiguousDominator true when more than one test tied for narrowest dominator - weaker evidence, caps confidence
     */
    public record Subsumption(String subsumedTest, int subsumedKillCount, String dominatorTest,
                               int dominatorKillCount, boolean ambiguousDominator) {
    }
}
