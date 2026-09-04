package dev.proofjava.analysis.mutation;

import java.util.List;

/**
 * One production {@code class#method(desc)} PIT mutated, with every mutant
 * generated for it (schema {@code $defs/mutatedMethod}). {@code
 * methodDescription} is the JVM method descriptor (disambiguates
 * overloads) - PIT's own {@code Location.getMethodDesc()}. {@code
 * firstLine}/{@code lastLine} span the mutants' line numbers, for a
 * finding's {@code startLine}/{@code endLine} without a second source read.
 */
public record MutatedMethod(String className, String methodName, String methodDescription,
                             int firstLine, int lastLine, List<Mutant> mutants) {
}
