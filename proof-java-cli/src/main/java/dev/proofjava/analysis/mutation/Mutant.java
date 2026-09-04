package dev.proofjava.analysis.mutation;

import java.util.List;

/**
 * One gregor mutant PIT generated for a method (schema {@code
 * $defs/mutant}). {@code status} is PIT's own {@link
 * org.pitest.mutationtest.DetectionStatus} name (e.g. {@code KILLED},
 * {@code SURVIVED}, {@code NO_COVERAGE}, {@code TIMED_OUT}). {@code
 * killingTests} is populated only when {@code setFullMutationMatrix(true)}
 * is set (M5/D-56) - every test that killed this mutant, not just the
 * first, since M4's redundancy gate needs the full kill set.
 */
public record Mutant(String mutator, int line, String status, List<String> killingTests) {
}
