package dev.coverdict.analysis.model;

/**
 * One entry of the verdict JSON's {@code findings} array (schema {@code
 * $defs/finding}). The four L0 rules anchor to exactly one test method, so
 * {@code testMethod} is always present and {@code productionMethod} always
 * null for them. M5's {@code PSEUDO_TESTED_METHOD} anchors to a production
 * method instead - the reverse: {@code productionMethod} present, {@code
 * testMethod} null. The schema leaves both optional and mutually exclusive
 * in practice, never validated as such (no v0.1 rule needs both).
 */
public record Finding(
    String rule,
    Severity severity,
    Confidence confidence,
    String module,
    String path,
    int startLine,
    int endLine,
    String testMethod,
    String message,
    String suggestedAction,
    String fingerprint,
    String productionMethod
) {
}
