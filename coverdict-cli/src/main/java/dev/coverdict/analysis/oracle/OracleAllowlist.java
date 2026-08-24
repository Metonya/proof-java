package dev.coverdict.analysis.oracle;

import java.util.List;
import java.util.Set;

/**
 * The v0.1 allowlist (docs/rules/README.md's "Recognized oracle APIs"),
 * fixed at HIGH-confidence type+method-name pairs. {@code customOracles}
 * configuration support (same doc) is out of scope this step - a later CLI
 * surface can extend {@link #isUnconditionalOracle} without touching how
 * calls are resolved.
 */
final class OracleAllowlist {

    static final String JUNIT5_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    static final String JUNIT4_ASSERT = "org.junit.Assert";
    static final String ASSERTJ_ASSERTIONS = "org.assertj.core.api.Assertions";
    static final String HAMCREST_MATCHER_ASSERT = "org.hamcrest.MatcherAssert";
    static final String MOCKITO = "org.mockito.Mockito";
    static final String MOCKITO_IN_ORDER = "org.mockito.InOrder";
    static final String MOCKITO_BDD = "org.mockito.BDDMockito";
    static final String TRUTH = "com.google.common.truth.Truth";

    static final Set<String> ALL_TYPES = Set.of(
        JUNIT5_ASSERTIONS, JUNIT4_ASSERT, ASSERTJ_ASSERTIONS,
        HAMCREST_MATCHER_ASSERT, MOCKITO, MOCKITO_IN_ORDER, MOCKITO_BDD, TRUTH);

    private static final String VERIFY = "verify";
    private static final String ASSERT_THAT = "assertThat";

    private static final List<String> ORACLE_SUGGESTIVE_PREFIXES =
        List.of("assert", VERIFY, "check", "expect", "should", "require", "fail");

    private OracleAllowlist() {
    }

    /** A call that is an oracle purely by (declaring type, method name) - no chain needed. */
    static boolean isUnconditionalOracle(String declaringTypeFqn, String methodName) {
        if (JUNIT5_ASSERTIONS.equals(declaringTypeFqn) || JUNIT4_ASSERT.equals(declaringTypeFqn)) {
            return methodName.startsWith("assert") || "fail".equals(methodName);
        }
        if (ASSERTJ_ASSERTIONS.equals(declaringTypeFqn)) {
            return "fail".equals(methodName);
        }
        if (HAMCREST_MATCHER_ASSERT.equals(declaringTypeFqn)) {
            return ASSERT_THAT.equals(methodName);
        }
        if (MOCKITO.equals(declaringTypeFqn)) {
            return VERIFY.equals(methodName) || "verifyNoInteractions".equals(methodName) || "verifyNoMoreInteractions".equals(methodName);
        }
        if (MOCKITO_IN_ORDER.equals(declaringTypeFqn)) {
            return VERIFY.equals(methodName);
        }
        return false;
    }

    /**
     * A call that only counts as an oracle when something else is chained
     * onto its result (README: "the chain itself counts as an oracle only if
     * a terminal assertion method is invoked on it").
     */
    static boolean isChainAnchor(String declaringTypeFqn, String methodName) {
        if (ASSERTJ_ASSERTIONS.equals(declaringTypeFqn)) {
            return ASSERT_THAT.equals(methodName) || "assertThatThrownBy".equals(methodName)
                || "assertThatCode".equals(methodName) || "assertThatExceptionOfType".equals(methodName);
        }
        if (MOCKITO_BDD.equals(declaringTypeFqn)) {
            return "then".equals(methodName);
        }
        return TRUTH.equals(declaringTypeFqn)
            && (ASSERT_THAT.equals(methodName) || "assertWithMessage".equals(methodName));
    }

    static boolean isOracleSuggestiveName(String methodName) {
        for (String prefix : ORACLE_SUGGESTIVE_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
