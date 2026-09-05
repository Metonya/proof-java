package dev.proofjava.analysis.redundancy;

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

    /**
     * JUnit 4 through the vintage engine, which names the class in
     * {@code [runner:...]} and the method in {@code [test:...]} rather than in
     * {@code [class:]}/{@code [method:]}. Found by running against google/gson,
     * whose suite-driven tests produce exactly this shape.
     */
    private static final Pattern JUNIT4_VINTAGE_ID =
        Pattern.compile("\\[runner:([^]]+)].*\\[test:([^](\\[]+)");

    /** What a fully qualified class name can contain - nothing else may be treated as one. */
    private static final Pattern PLAUSIBLE_CLASS_NAME = Pattern.compile("[\\w$]+(\\.[\\w$]+)*");

    /**
     * What a Java method name can contain. Generated suite descriptors put
     * things like {@code JsonArray#asList %5Bcollection size%3A zero%5D} where a
     * method name would go; those name no method this can locate, so they are
     * unresolved rather than searched for.
     */
    private static final Pattern PLAUSIBLE_METHOD_NAME = Pattern.compile("[\\w$]+");

    private TestIdentity() {
    }

    /** @return null when the raw id does not match a recognized shape - callers skip enrichment rather than guess. */
    static Parsed parse(String rawTestId) {
        Matcher unique = JUNIT5_UNIQUE_ID.matcher(rawTestId);
        if (unique.find()) {
            return parsedOrNull(unique.group(1), unique.group(2));
        }
        Matcher vintage = JUNIT4_VINTAGE_ID.matcher(rawTestId);
        if (vintage.find()) {
            return parsedOrNull(vintage.group(1), vintage.group(2));
        }
        int hash = rawTestId.indexOf('#');
        if (hash > 0 && hash < rawTestId.length() - 1) {
            String className = rawTestId.substring(0, hash);
            String rest = rawTestId.substring(hash + 1);
            int paren = rest.indexOf('(');
            String methodName = paren >= 0 ? rest.substring(0, paren) : rest;
            if (!methodName.isEmpty()) {
                return parsedOrNull(className, methodName);
            }
        }
        return null;
    }

    /**
     * Guards every path out of {@link #parse}: an id shape none of the patterns
     * really understood used to come back with the whole raw string as the
     * "class name", which {@code TestLocator} then turned into a file path.
     * On a gson vintage-suite id that produced a
     * {@code java.nio.file.InvalidPathException} and aborted the whole analysis.
     * Unrecognized is null - callers skip the enrichment (hard rule 3a).
     */
    private static Parsed parsedOrNull(String className, String methodName) {
        if (className == null || methodName == null
            || !PLAUSIBLE_CLASS_NAME.matcher(className).matches()
            || !PLAUSIBLE_METHOD_NAME.matcher(methodName).matches()) {
            return null;
        }
        return new Parsed(className, methodName);
    }

    record Parsed(String className, String methodName) {
    }
}
