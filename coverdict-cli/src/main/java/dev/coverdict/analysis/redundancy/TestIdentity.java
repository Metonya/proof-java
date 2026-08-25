package dev.coverdict.analysis.redundancy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one raw PIT killing-test id string (whatever {@code
 * MutationResult.getKillingTests()} produced, passed through unchanged since
 * {@code MutationResultAccumulator} - D-49 notes this can be a JUnit5 {@code
 * UniqueId} or a simpler {@code Class#method} shape depending on the target's
 * test engine, and the real format was never confirmed against a live run
 * that actually killed a mutant) into a class name and a bare method name,
 * best-effort. {@link SubsumptionAnalyzer} never calls this - kill-set
 * comparison works on the raw strings directly, format-agnostic. Only {@link
 * SubsumedTestRule}'s path/line resolution (an enrichment, not the core
 * claim) needs to know what class and method a raw id names.
 */
final class TestIdentity {

    private static final Pattern JUNIT5_UNIQUE_ID =
        Pattern.compile("\\[class:([^]]+)].*\\[method:([^](]+)");

    private TestIdentity() {
    }

    /** @return null when the raw id does not match a recognized shape - callers skip enrichment rather than guess. */
    static Parsed parse(String rawTestId) {
        Matcher unique = JUNIT5_UNIQUE_ID.matcher(rawTestId);
        if (unique.find()) {
            return new Parsed(unique.group(1), unique.group(2));
        }
        int hash = rawTestId.indexOf('#');
        if (hash > 0 && hash < rawTestId.length() - 1) {
            String className = rawTestId.substring(0, hash);
            String rest = rawTestId.substring(hash + 1);
            int paren = rest.indexOf('(');
            String methodName = paren >= 0 ? rest.substring(0, paren) : rest;
            if (!methodName.isEmpty()) {
                return new Parsed(className, methodName);
            }
        }
        return null;
    }

    record Parsed(String className, String methodName) {
    }
}
