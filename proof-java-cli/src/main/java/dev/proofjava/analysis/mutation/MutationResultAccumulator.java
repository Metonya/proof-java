package dev.proofjava.analysis.mutation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.MutationDetails;

import dev.proofjava.analysis.model.AnalysisReason;

/**
 * Groups PIT's per-mutant {@link ClassMutationResults} into proof-java's
 * per-method {@link MutatedMethod} shape - {@link
 * dev.proofjava.analysis.pertest.BlockLineResolver}'s sibling for L3. No
 * line-map resolution is needed here (unlike L2): {@link
 * MutationDetails#getLineNumber()} is already a source line.
 *
 * <p>{@code TreeMap} keys are {@code "<className>#<methodName>#<methodDesc>"}:
 * none of the three parts can contain {@code '#'}, and the method
 * descriptor disambiguates overloads that share a name - string order over
 * the combined key gives a deterministic, method-grouped iteration order.
 */
public final class MutationResultAccumulator {

    /**
     * SECURITY-POLICY.md #2 precedent, same policy as {@link
     * dev.proofjava.analysis.pertest.BlockLineResolver#MAX_LINE_RECORDS}:
     * a pathological diff-scoped target still degrades to a warning rather
     * than an unbounded JSON. Truncation is all-or-nothing.
     */
    static final int MAX_MUTANT_RECORDS = 200_000;

    private static final Comparator<Mutant> MUTANT_ORDER =
        Comparator.comparingInt(Mutant::line).thenComparing(Mutant::mutator).thenComparing(Mutant::status);

    private MutationResultAccumulator() {
    }

    public static MutationModuleEvidence accumulate(String moduleId, List<ClassMutationResults> results) {
        Map<String, List<MutationResult>> byMethod = new TreeMap<>();
        for (ClassMutationResults classResult : results) {
            for (MutationResult mutationResult : classResult.getMutations()) {
                Location location = mutationResult.getDetails().getId().getLocation();
                String key = location.getClassName().asJavaName() + "#" + location.getMethodName()
                    + "#" + location.getMethodDesc();
                byMethod.computeIfAbsent(key, k -> new ArrayList<>()).add(mutationResult);
            }
        }

        List<MutatedMethod> methods = toMethods(byMethod);
        int totalMutants = countMutants(methods);
        if (totalMutants > MAX_MUTANT_RECORDS) {
            AnalysisReason truncated = new AnalysisReason("MUTATION_TRUNCATED",
                "Module '" + moduleId + "' mutation evidence carries " + totalMutants + " mutant records, over the "
                    + MAX_MUTANT_RECORDS + " limit (SECURITY-POLICY.md #2); dropped rather than partially reported.",
                null, moduleId, totalMutants);
            return new MutationModuleEvidence(moduleId, List.of(), List.of(truncated));
        }
        return new MutationModuleEvidence(moduleId, methods, List.of());
    }

    private static List<MutatedMethod> toMethods(Map<String, List<MutationResult>> byMethod) {
        List<MutatedMethod> methods = new ArrayList<>();
        for (Map.Entry<String, List<MutationResult>> entry : byMethod.entrySet()) {
            String[] parts = entry.getKey().split("#", 3);
            String className = parts[0];
            String methodName = parts[1];
            String methodDescription = parts[2];

            List<Mutant> mutants = new ArrayList<>();
            int firstLine = Integer.MAX_VALUE;
            int lastLine = Integer.MIN_VALUE;
            for (MutationResult result : entry.getValue()) {
                int line = result.getDetails().getLineNumber();
                firstLine = Math.min(firstLine, line);
                lastLine = Math.max(lastLine, line);
                mutants.add(new Mutant(result.getDetails().getMutator(), line, result.getStatus().name(),
                    List.copyOf(result.getKillingTests())));
            }
            mutants.sort(MUTANT_ORDER);
            methods.add(new MutatedMethod(className, methodName, methodDescription, firstLine, lastLine,
                List.copyOf(mutants)));
        }
        return methods;
    }

    private static int countMutants(List<MutatedMethod> methods) {
        int total = 0;
        for (MutatedMethod method : methods) {
            total += method.mutants().size();
        }
        return total;
    }
}
