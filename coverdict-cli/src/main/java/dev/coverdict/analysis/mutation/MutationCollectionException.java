package dev.coverdict.analysis.mutation;

/**
 * A mutation evidence collection failure for one module - process spawn
 * failure, a budget timeout, a non-zero exit, an unreadable result file.
 * Always caught by {@link MutationCollector} and turned into a warning
 * (never {@link dev.coverdict.analysis.AnalysisException}): L3 evidence is
 * opt-in and its absence never makes the run incomplete (D-46 pattern).
 * {@link #reasonCode} lets the collector distinguish a budget exhaustion
 * ({@code MUTATION_BUDGET_EXCEEDED}) from every other failure ({@code
 * MUTATION_COLLECTION_FAILED}) without string-matching the message.
 */
final class MutationCollectionException extends RuntimeException {

    static final String COLLECTION_FAILED = "MUTATION_COLLECTION_FAILED";
    static final String BUDGET_EXCEEDED = "MUTATION_BUDGET_EXCEEDED";

    private final String reasonCode;

    MutationCollectionException(String message) {
        this(message, COLLECTION_FAILED);
    }

    MutationCollectionException(String message, String reasonCode) {
        super(message);
        this.reasonCode = reasonCode;
    }

    MutationCollectionException(String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = COLLECTION_FAILED;
    }

    String reasonCode() {
        return reasonCode;
    }
}
