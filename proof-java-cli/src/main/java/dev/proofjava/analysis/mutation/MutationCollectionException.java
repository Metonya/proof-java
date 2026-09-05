package dev.proofjava.analysis.mutation;

/**
 * A mutation evidence collection failure for one module - process spawn
 * failure, a budget timeout, a non-zero exit, an unreadable result file.
 * Always caught by {@link MutationCollector}, which records it as an
 * incomplete reason (D-85): {@code --mutation-report} was asked for and did not
 * produce what it promised, so the run is not complete. {@link #reasonCode}
 * lets the collector distinguish a budget exhaustion ({@code
 * MUTATION_BUDGET_EXCEEDED}) from every other failure ({@code
 * MUTATION_COLLECTION_FAILED}) without string-matching the message.
 *
 * <p>{@link #partialEvidence()} carries whatever the run did measure before it
 * was stopped. Since D-85 the listener flushes as classes complete, so a budget
 * kill usually leaves real evidence on disk - reporting the classes that were
 * measured, while naming the run incomplete because the rest were not, is
 * strictly more honest than discarding both.
 */
final class MutationCollectionException extends RuntimeException {

    static final String COLLECTION_FAILED = "MUTATION_COLLECTION_FAILED";
    static final String BUDGET_EXCEEDED = "MUTATION_BUDGET_EXCEEDED";

    private final String reasonCode;
    private final transient MutationModuleEvidence partialEvidence;

    MutationCollectionException(String message) {
        this(message, COLLECTION_FAILED);
    }

    MutationCollectionException(String message, String reasonCode) {
        this(message, reasonCode, null);
    }

    MutationCollectionException(String message, String reasonCode, MutationModuleEvidence partialEvidence) {
        super(message);
        this.reasonCode = reasonCode;
        this.partialEvidence = partialEvidence;
    }

    MutationCollectionException(String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = COLLECTION_FAILED;
        this.partialEvidence = null;
    }

    String reasonCode() {
        return reasonCode;
    }

    /** @return what the stopped run had already measured, when anything survived. */
    java.util.Optional<MutationModuleEvidence> partialEvidence() {
        return java.util.Optional.ofNullable(partialEvidence);
    }
}
