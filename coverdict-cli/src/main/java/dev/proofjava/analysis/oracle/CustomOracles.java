package dev.proofjava.analysis.oracle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@code customOracles} configuration (docs/rules/README.md): a list of
 * {@code fully.qualified.Type#methodPattern} entries, glob {@code *} allowed
 * in the method part only. A resolved call matching an entry is an oracle.
 *
 * <p>This is the configured extension of D-24's built-in allowlist, and it
 * closes the gap every corpus phase kept labelling "D-17 territory": gson's
 * {@code MoreAsserts}, assertj's {@code AssertionsUtil}, junit-framework's
 * {@code PreconditionAssertions} are all real oracles living in a helper class
 * outside the compilation unit, which v0.1 could recognize only if the user
 * could name them - and until now there was no surface to name them on.
 *
 * <p>Entries behave like {@link OracleAllowlist#isUnconditionalOracle}, not
 * like a chain anchor: the configured call itself is the oracle, with no
 * requirement that anything be chained onto its result. A user who needs the
 * chain-anchor shape is describing a fluent assertion library, which is a
 * built-in allowlist question (D-34), not a per-repo helper.
 */
final class CustomOracles {

    private static final CustomOracles NONE = new CustomOracles(List.of());

    private final List<Entry> entries;
    private final Set<String> types;

    private CustomOracles(List<Entry> entries) {
        this.entries = entries;
        Set<String> declaredTypes = new LinkedHashSet<>();
        for (Entry entry : entries) {
            declaredTypes.add(entry.typeFqn());
        }
        this.types = Set.copyOf(declaredTypes);
    }

    static CustomOracles none() {
        return NONE;
    }

    /**
     * @param configured entries already validated for shape by
     *                   {@code ConfigLoader} (exactly one {@code #}, both sides
     *                   non-empty), so this parse cannot fail
     */
    static CustomOracles of(List<String> configured) {
        if (configured.isEmpty()) {
            return NONE;
        }
        List<Entry> parsed = new ArrayList<>();
        for (String raw : configured) {
            int hash = raw.indexOf('#');
            parsed.add(new Entry(raw.substring(0, hash), raw.substring(hash + 1)));
        }
        return new CustomOracles(List.copyOf(parsed));
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * The configured types, so import-anchoring (D-28's second tier) can reach
     * them the same way it reaches built-in allowlist types. Without this a
     * custom oracle would only ever be recognized when the Symbol Solver
     * happened to resolve it - which, for a helper in another module with no
     * {@code --classpath}, is exactly the case that fails.
     */
    Set<String> types() {
        return types;
    }

    boolean matches(String declaringTypeFqn, String methodName) {
        for (Entry entry : entries) {
            if (entry.typeFqn().equals(declaringTypeFqn) && entry.matchesMethod(methodName)) {
                return true;
            }
        }
        return false;
    }

    private record Entry(String typeFqn, String methodPattern) {

        boolean matchesMethod(String methodName) {
            return globMatches(methodPattern, methodName);
        }

        /**
         * Glob over the method name only: {@code *} stands for any run of
         * characters, everything else is literal. Written out rather than
         * translated to a regex so a pattern containing regex metacharacters
         * (a {@code $} in a nested-class-style name, say) cannot change what
         * matching means.
         */
        private static boolean globMatches(String pattern, String value) {
            int p = 0;
            int v = 0;
            int starAt = -1;
            int matchedUpTo = 0;
            while (v < value.length()) {
                if (p < pattern.length() && pattern.charAt(p) == '*') {
                    starAt = p++;
                    matchedUpTo = v;
                } else if (p < pattern.length() && pattern.charAt(p) == value.charAt(v)) {
                    p++;
                    v++;
                } else if (starAt >= 0) {
                    p = starAt + 1;
                    v = ++matchedUpTo;
                } else {
                    return false;
                }
            }
            while (p < pattern.length() && pattern.charAt(p) == '*') {
                p++;
            }
            return p == pattern.length();
        }
    }
}
