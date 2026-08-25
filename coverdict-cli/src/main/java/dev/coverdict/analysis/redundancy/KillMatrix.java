package dev.coverdict.analysis.redundancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.coverdict.analysis.mutation.MutatedMethod;
import dev.coverdict.analysis.mutation.Mutant;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;

/**
 * D-61's raw ingredient: one module's L3 mutant-kill data reshaped into two
 * indexes over a synthetic per-mutant identity - {@code
 * "<class>#<method><desc>#<mutator>#<line>#<ordinal>"}, unique within one
 * {@link MutationModuleEvidence} because {@code ordinal} disambiguates
 * whatever duplicate (mutator, line) pairs a single method might carry
 * (unconfirmed against a real PIT run whether this ever actually happens -
 * the ordinal makes it safe either way rather than assuming it cannot).
 *
 * <p>Only {@code KILLED} mutants with a non-empty {@code killingTests} enter
 * either index - {@code SURVIVED}/{@code NO_COVERAGE}/inconclusive-status
 * mutants carry no kill-set signal and would otherwise contribute nothing
 * but noise to the intersection {@link SubsumptionAnalyzer} computes.
 */
public final class KillMatrix {

    private static final String KILLED = "KILLED";

    private KillMatrix() {
    }

    public static Data build(MutationModuleEvidence evidence) {
        Map<String, Set<String>> killsByTest = new TreeMap<>();
        Map<String, Set<String>> testsByMutant = new TreeMap<>();

        for (MutatedMethod method : evidence.methods()) {
            String methodKey = method.className() + "#" + method.methodName() + method.methodDescription();
            int ordinal = 0;
            for (Mutant mutant : method.mutants()) {
                String mutantId = methodKey + "#" + mutant.mutator() + "#" + mutant.line() + "#" + ordinal++;
                if (!KILLED.equals(mutant.status()) || mutant.killingTests().isEmpty()) {
                    continue;
                }
                Set<String> killers = new TreeSet<>(mutant.killingTests());
                testsByMutant.put(mutantId, killers);
                for (String test : killers) {
                    killsByTest.computeIfAbsent(test, t -> new TreeSet<>()).add(mutantId);
                }
            }
        }

        return new Data(copyOfSets(killsByTest), copyOfSets(testsByMutant));
    }

    private static Map<String, Set<String>> copyOfSets(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /**
     * @param killsByTest   test id -> set of mutant ids it killed (never contains an empty set - such a test is simply absent)
     * @param testsByMutant mutant id -> set of test ids that killed it (never empty, by construction above)
     */
    public record Data(Map<String, Set<String>> killsByTest, Map<String, Set<String>> testsByMutant) {

        public List<String> testsWithKills() {
            return new ArrayList<>(killsByTest.keySet());
        }
    }
}
