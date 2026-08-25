package dev.coverdict.analysis.oracle;

import java.util.Optional;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import dev.coverdict.analysis.model.RuleIds;
import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Severity;

/** docs/rules/NULL_CHECK_ONLY.md */
final class NullCheckOnlyRule {

    static final String RULE_ID = RuleIds.NULL_CHECK_ONLY;
    static final Severity SEVERITY = Severity.INFO;
    static final String SUGGESTED_ACTION =
        "The only verification is non-nullness; consider asserting on the value's content. Advisory.";

    private NullCheckOnlyRule() {
    }

    static Optional<RuleFinding> evaluate(MethodDeclaration testMethod, TraversalResult traversal) {
        if (traversal.oracles().isEmpty()) {
            return Optional.empty(); // "has at least one oracle" is a precondition
        }
        if (traversal.oracles().stream().anyMatch(occ -> !isNullCheck(occ))) {
            return Optional.empty(); // any content-verifying oracle disqualifies the whole method
        }
        if (traversal.anyUnresolvedSuggestive()) {
            return Optional.empty(); // "only null checks" is unprovable with a hidden oracle-suggestive call around
        }
        Confidence confidence = traversal.anyUnresolvedNonSuggestive() ? Confidence.MEDIUM : Confidence.HIGH;
        return Optional.of(new RuleFinding(confidence,
            "Test '" + testMethod.getNameAsString() + "' only verifies non-nullness; content is never checked."));
    }

    private static boolean isNullCheck(OracleOccurrence occ) {
        if (occ.anchorCall() == occ.terminalCall()) {
            if (OracleAllowlist.JUNIT5_ASSERTIONS.equals(occ.declaringTypeFqn()) || OracleAllowlist.JUNIT4_ASSERT.equals(occ.declaringTypeFqn())) {
                return "assertNotNull".equals(occ.methodName());
            }
            if (OracleAllowlist.HAMCREST_MATCHER_ASSERT.equals(occ.declaringTypeFqn())) {
                return isHamcrestNotNullCheck(occ.anchorCall());
            }
            return false;
        }
        // AssertJ fluent chain: assertThat(x).isNotNull() as the only terminal call in the chain.
        return OracleAllowlist.ASSERTJ_ASSERTIONS.equals(occ.declaringTypeFqn()) && "isNotNull".equals(occ.methodName());
    }

    private static boolean isHamcrestNotNullCheck(MethodCallExpr assertThatCall) {
        if (assertThatCall.getArguments().size() < 2) {
            return false;
        }
        return isNotNullMatcher(assertThatCall.getArguments().get(1));
    }

    private static boolean isNotNullMatcher(Expression matcherArg) {
        if (!(matcherArg instanceof MethodCallExpr mce)) {
            return false;
        }
        if ("notNullValue".equals(mce.getNameAsString())) {
            return true;
        }
        return "is".equals(mce.getNameAsString()) && mce.getArguments().size() == 1 && isNotNullMatcher(mce.getArguments().get(0));
    }
}
