package dev.coverdict.analysis.model;

/**
 * One entry of the verdict JSON's {@code incompleteReasons} or
 * {@code warnings} array (schema {@code $defs/reason}): a stable
 * {@code code}, a human message, and optional {@code path}/{@code module}
 * context. Never a silent drop (hard rule 3a) - every skipped or degraded
 * piece of evidence becomes one of these.
 *
 * @param count optional machine-readable quantity for reasons that are
 *              inherently about "how many" ({@code SUPPRESSED_FINDINGS},
 *              D-42). Null for every other reason. The number is deliberately
 *              a field rather than only prose inside {@code message}: hard
 *              rule 7 makes the JSON the product, and a count an agent has to
 *              regex out of a sentence is not a contract.
 */
public record AnalysisReason(String code, String message, String path, String module, Integer count) {

    public AnalysisReason(String code, String message) {
        this(code, message, null, null, null);
    }

    public AnalysisReason(String code, String message, String path) {
        this(code, message, path, null, null);
    }

    public AnalysisReason(String code, String message, String path, String module) {
        this(code, message, path, module, null);
    }
}
