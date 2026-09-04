package dev.proofjava.analysis;

/**
 * Signals a structured, fail-closed analysis problem: malformed input,
 * ambiguous evidence, or a rejected duplicate identity (hard rule 3a). The
 * {@code code} matches the schema's {@code reason.code} pattern
 * ({@code ^[A-Z][A-Z0-9_]+$}) and becomes an {@code incompleteReasons} or
 * {@code warnings} entry in the verdict JSON - never silently swallowed.
 */
public class AnalysisException extends RuntimeException {

    private final String code;

    public AnalysisException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AnalysisException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
