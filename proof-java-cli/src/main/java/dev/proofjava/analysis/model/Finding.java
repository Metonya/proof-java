package dev.proofjava.analysis.model;

/**
 * One entry of the verdict JSON's {@code findings} array (schema {@code
 * $defs/finding}). The four L0 rules anchor to exactly one test method, so
 * {@code testMethod} is always present and {@code productionMethod} always
 * null for them. M5's {@code PSEUDO_TESTED_METHOD} anchors to a production
 * method instead - the reverse: {@code productionMethod} present, {@code
 * testMethod} null. The schema leaves both optional and mutually exclusive
 * in practice, never validated as such (no v0.1 rule needs both).
 *
 * <p>{@code relatedTestMethod}/{@code relatedPath} exist only for M4's
 * {@code SUBSUMED_TEST} (D-61): the primary {@code testMethod}/{@code path}/
 * {@code startLine}/{@code endLine} anchor the subsumed (redundant) test, and
 * these two carry the dominator test that subsumes it - the one other rule
 * shape needing a second test identity in the same finding. Null for every
 * other rule.
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
    String productionMethod,
    String relatedTestMethod,
    String relatedPath
) {
    public Finding(String rule, Severity severity, Confidence confidence, String module, String path,
                    int startLine, int endLine, String testMethod, String message, String suggestedAction,
                    String fingerprint, String productionMethod) {
        this(rule, severity, confidence, module, path, startLine, endLine, testMethod, message, suggestedAction,
            fingerprint, productionMethod, null, null);
    }
}
