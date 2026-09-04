package dev.proofjava.analysis.pertest;

/**
 * A per-test evidence collection failure for one module - process spawn
 * failure, an unreadable result file. Always caught by {@link
 * PerTestCollector} and turned into a {@code PER_TEST_COLLECTION_FAILED}
 * warning (never {@link dev.proofjava.analysis.AnalysisException}): L2
 * evidence is opt-in and its absence never makes the run incomplete (D-46).
 */
final class PerTestCollectionException extends RuntimeException {

    PerTestCollectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /** D-64: a timeout with no exported coverage has no underlying exception - the subprocess output tail carries the diagnosis instead. */
    PerTestCollectionException(String message) {
        super(message);
    }
}
