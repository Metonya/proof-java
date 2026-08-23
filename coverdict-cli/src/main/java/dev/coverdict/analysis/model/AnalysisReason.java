package dev.coverdict.analysis.model;

/**
 * One entry of the verdict JSON's {@code incompleteReasons} or
 * {@code warnings} array (schema {@code $defs/reason}): a stable
 * {@code code}, a human message, and optional {@code path}/{@code module}
 * context. Never a silent drop (hard rule 3a) - every skipped or degraded
 * piece of evidence becomes one of these.
 */
public record AnalysisReason(String code, String message, String path, String module) {

    public AnalysisReason(String code, String message) {
        this(code, message, null, null);
    }

    public AnalysisReason(String code, String message, String path) {
        this(code, message, path, null);
    }
}
