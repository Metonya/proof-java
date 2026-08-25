package dev.coverdict.analysis.mutation;

/**
 * A mutation evidence collection failure for one module - process spawn
 * failure, a budget timeout, a non-zero exit, an unreadable result file.
 * Always caught by {@link MutationCollector} and turned into a {@code
 * MUTATION_COLLECTION_FAILED} (or {@code MUTATION_BUDGET_EXCEEDED}) warning
 * (never {@link dev.coverdict.analysis.AnalysisException}): L3 evidence is
 * opt-in and its absence never makes the run incomplete (D-46 pattern).
 */
final class MutationCollectionException extends RuntimeException {

    MutationCollectionException(String message) {
        super(message);
    }

    MutationCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
