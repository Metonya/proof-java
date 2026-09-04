package dev.proofjava.analysis.model;

import java.util.Set;

/**
 * The v0.1 rule ids (docs/rules/), in one place so nothing has to restate
 * them. Both the four rule classes and the config reader's {@code
 * suppressions.rule} validation resolve here, which is what keeps
 * {@code schema/proof-config.schema.json}'s enum, the reader, and the
 * engine from drifting into three different lists.
 */
public final class RuleIds {

    public static final String NO_RECOGNIZED_ORACLE = "NO_RECOGNIZED_ORACLE";
    public static final String TAUTOLOGICAL_ORACLE = "TAUTOLOGICAL_ORACLE";
    public static final String CATCH_ORACLE_WITHOUT_FAIL = "CATCH_ORACLE_WITHOUT_FAIL";
    public static final String NULL_CHECK_ONLY = "NULL_CHECK_ONLY";
    public static final String PSEUDO_TESTED_METHOD = "PSEUDO_TESTED_METHOD";
    public static final String SUBSUMED_TEST = "SUBSUMED_TEST";

    public static final Set<String> ALL = Set.of(
        NO_RECOGNIZED_ORACLE, TAUTOLOGICAL_ORACLE, CATCH_ORACLE_WITHOUT_FAIL, NULL_CHECK_ONLY, PSEUDO_TESTED_METHOD,
        SUBSUMED_TEST);

    private RuleIds() {
    }
}
