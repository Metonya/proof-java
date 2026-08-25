package dev.coverdict.analysis.oracle;

import java.util.Optional;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import dev.coverdict.analysis.model.RuleIds;
import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Severity;

/** docs/rules/NO_RECOGNIZED_ORACLE.md */
final class NoRecognizedOracleRule {

    static final String RULE_ID = RuleIds.NO_RECOGNIZED_ORACLE;
    static final Severity SEVERITY = Severity.WARNING;
    static final String SUGGESTED_ACTION =
        "Add an assertion on the observed behavior, or register the helper as a custom oracle in configuration.";

    private NoRecognizedOracleRule() {
    }

    static Optional<RuleFinding> evaluate(MethodDeclaration testMethod, TraversalResult traversal) {
        if (TestMethods.isTestFactory(testMethod)) {
            return Optional.empty(); // never fires on @TestFactory (spec: dynamic-test lambdas not traversed)
        }
        Optional<com.github.javaparser.ast.stmt.BlockStmt> body = testMethod.getBody();
        if (body.isEmpty() || body.get().getStatements().isEmpty()) {
            return Optional.empty(); // empty body is a different smell, out of v0.1 scope
        }
        if (!traversal.oracles().isEmpty() || TestMethods.hasExpectedExceptionAnnotation(testMethod)) {
            return Optional.empty();
        }

        Optional<MethodCallExpr> firstSuggestiveUnresolved = traversal.unresolvedCalls()
            .filter(c -> OracleAllowlist.isOracleSuggestiveName(c.getNameAsString()))
            .findFirst();
        if (firstSuggestiveUnresolved.isPresent()) {
            return Optional.of(new RuleFinding(Confidence.INCONCLUSIVE,
                "Test '" + testMethod.getNameAsString() + "' calls an unresolved '"
                    + firstSuggestiveUnresolved.get().getNameAsString()
                    + "' that looks oracle-suggestive; it could not be resolved to confirm."));
        }
        Confidence confidence = traversal.anyUnresolvedNonSuggestive() ? Confidence.MEDIUM : Confidence.HIGH;
        return Optional.of(new RuleFinding(confidence,
            "Test '" + testMethod.getNameAsString()
                + "' contains no recognized assertion, verification, or expected exception."));
    }
}
