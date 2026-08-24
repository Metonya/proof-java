package dev.coverdict.analysis.model;

/**
 * One entry of the verdict JSON's {@code findings} array (schema {@code
 * $defs/finding}). Every v0.1 rule anchors to exactly one test method, so
 * {@code testMethod} is always present here even though the schema leaves it
 * optional for a future rule that might not anchor to one.
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
    String fingerprint
) {
}
