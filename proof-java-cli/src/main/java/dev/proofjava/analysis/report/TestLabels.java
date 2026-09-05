package dev.proofjava.analysis.report;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw engine test id into something a person can read in a report cell.
 *
 * <p>D-90: a real gson report showed killing tests as walls of text like
 * {@code com.google.gson.JsonArrayAsListSuiteTest.[engine:junit-vintage]/[runner:
 * com.google.gson.JsonArrayAsListSuiteTest]/[test:JsonArray#asList %5Bcollection
 * size%3A several%5D]/[test:com.google.common.collect.testing.testers
 * .CollectionContainsAllTester]/...}, dozens per mutant. The percent escapes are
 * URL encoding for {@code [} and {@code :}, and the bracket scaffolding is the
 * engine's addressing, not information. A reader's question is which test killed
 * this mutant, and the answer fits in a few words.
 *
 * <p>The raw id stays in the verdict JSON, which is the machine contract. This
 * only shapes what the HTML shows.
 */
final class TestLabels {

    private static final Pattern JUNIT5 = Pattern.compile("\\[class:([^]]+)].*?\\[method:([^](]+)");
    private static final Pattern VINTAGE = Pattern.compile("\\[runner:([^]]+)].*?\\[test:([^](\\[]+)");
    /**
     * SonarQube java:S5852 flagged this as a possible DoS-by-backtracking
     * hotspot; reviewed and hardened rather than left as-is. No nested
     * quantifier ambiguity exists here - {@code #} is outside {@code [\w.$]},
     * so the first group's greedy match can never cross into the second
     * group's territory and never needs to backtrack past a real {@code #}.
     * Possessive quantifiers make that explicit and remove even the linear
     * backtracking the plain {@code +} would otherwise attempt on a
     * non-matching tail.
     */
    private static final Pattern PLAIN = Pattern.compile("([\\w.$]++)#([\\w$]++)");

    private TestLabels() {
    }

    /** @return {@code SimpleClass#method}, or a decoded and shortened form when nothing parses. */
    static String readable(String rawTestId) {
        if (rawTestId == null || rawTestId.isBlank()) {
            return "";
        }
        String decoded = decodePercentEscapes(rawTestId);

        Matcher junit5 = JUNIT5.matcher(decoded);
        if (junit5.find()) {
            return shorten(simpleName(junit5.group(1)) + "#" + junit5.group(2).trim());
        }
        Matcher vintage = VINTAGE.matcher(decoded);
        if (vintage.find()) {
            return shorten(simpleName(vintage.group(1)) + "#" + vintage.group(2).trim());
        }
        Matcher plain = PLAIN.matcher(decoded);
        if (plain.find()) {
            return shorten(simpleName(plain.group(1)) + "#" + plain.group(2));
        }
        return shorten(decoded);
    }

    /**
     * Engine ids percent-encode the characters they use structurally, so
     * {@code %5B} and {@code %3A} appear literally in a suite descriptor. Decoded
     * by hand rather than with {@code URLDecoder}, which would also turn {@code +}
     * into a space and mangle method names that legitimately contain one.
     */
    private static String decodePercentEscapes(String raw) {
        if (raw.indexOf('%') < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '%' && i + 2 < raw.length() && isHex(raw.charAt(i + 1)) && isHex(raw.charAt(i + 2))) {
                out.append((char) Integer.parseInt(raw.substring(i + 1, i + 3), 16));
                i += 3;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String simpleName(String fqn) {
        String trimmed = fqn.trim();
        int lastDot = trimmed.lastIndexOf('.');
        return lastDot < 0 ? trimmed : trimmed.substring(lastDot + 1);
    }

    /** A table cell, not a document: a generated suite descriptor stays long even after the scaffolding is stripped. */
    private static String shorten(String value) {
        return value.length() <= 60 ? value : value.substring(0, 57) + "...";
    }

    /**
     * {@code org.pitest.mutationtest.engine.gregor.mutators.returns
     * .BooleanFalseReturnValsMutator} to {@code BooleanFalseReturnVals}. The full
     * name was wrapping into unreadable fragments in a narrow table column.
     */
    static String shortMutator(String mutator) {
        if (mutator == null || mutator.isBlank()) {
            return "";
        }
        String simple = simpleName(mutator);
        return simple.endsWith("Mutator") ? simple.substring(0, simple.length() - "Mutator".length()) : simple;
    }
}
