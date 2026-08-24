package dev.coverdict.analysis.oracle;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;

import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Severity;

/**
 * docs/rules/TAUTOLOGICAL_ORACLE.md. Only inspects direct JUnit-style calls
 * (assertEquals/assertTrue/assertFalse/assertNotNull) - every closed-list
 * pattern example in the spec is this shape; AssertJ fluent-chain argument
 * inspection is not attempted (out of scope this step).
 */
final class TautologicalOracleRule {

    static final String RULE_ID = "TAUTOLOGICAL_ORACLE";
    static final Severity SEVERITY = Severity.WARNING;
    static final String SUGGESTED_ACTION =
        "This assertion holds for any implementation; assert on a value produced by the code under test.";

    private static final Set<BinaryExpr.Operator> FOLDABLE_OPERATORS =
        EnumSet.of(BinaryExpr.Operator.PLUS, BinaryExpr.Operator.MINUS, BinaryExpr.Operator.MULTIPLY,
            BinaryExpr.Operator.DIVIDE, BinaryExpr.Operator.REMAINDER);

    private TautologicalOracleRule() {
    }

    static Optional<RuleFinding> evaluate(MethodDeclaration testMethod, TraversalResult traversal) {
        String testClassFqn = testMethod.findAncestor(ClassOrInterfaceDeclaration.class)
            .flatMap(TypeDeclaration::getFullyQualifiedName)
            .orElse(null);

        for (OracleOccurrence occ : traversal.oracles()) {
            // Fluent-chain argument shapes are not inspected (see class javadoc); only a
            // direct JUnit-style call (anchor == terminal) is a candidate for these patterns.
            boolean isDirectJunitCall = occ.anchorCall() == occ.terminalCall()
                && (OracleAllowlist.JUNIT5_ASSERTIONS.equals(occ.declaringTypeFqn())
                    || OracleAllowlist.JUNIT4_ASSERT.equals(occ.declaringTypeFqn()));
            if (isDirectJunitCall) {
                Optional<RuleFinding> finding = matchPattern(occ.methodName(), occ.anchorCall(), testClassFqn);
                if (finding.isPresent()) {
                    return finding;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<RuleFinding> matchPattern(String methodName, MethodCallExpr call, String testClassFqn) {
        List<Expression> args = call.getArguments();
        if ("assertEquals".equals(methodName) && args.size() >= 2) {
            Optional<RuleFinding> f = matchAssertEquals(args.get(0), args.get(1), testClassFqn);
            if (f.isPresent()) {
                return f;
            }
        }
        if (("assertTrue".equals(methodName) && !args.isEmpty() && isBooleanLiteral(args.get(0), true))
            || ("assertFalse".equals(methodName) && !args.isEmpty() && isBooleanLiteral(args.get(0), false))) {
            return Optional.of(new RuleFinding(Confidence.HIGH, "Assertion on a literal boolean holds for any implementation."));
        }
        if ("assertNotNull".equals(methodName) && !args.isEmpty() && args.get(0) instanceof ObjectCreationExpr) {
            return Optional.of(new RuleFinding(Confidence.HIGH, "A freshly constructed object can never be null; this assertion cannot fail."));
        }
        return Optional.empty();
    }

    private static Optional<RuleFinding> matchAssertEquals(Expression a, Expression b, String testClassFqn) {
        if (isPlainConstant(a, testClassFqn) && isPlainConstant(b, testClassFqn)) {
            return Optional.of(new RuleFinding(Confidence.HIGH, "Both operands are compile-time constants; this assertion holds for any implementation."));
        }
        if (a.toString().equals(b.toString()) && a.findAll(MethodCallExpr.class).isEmpty() && b.findAll(MethodCallExpr.class).isEmpty()) {
            if (a instanceof NameExpr || a instanceof FieldAccessExpr) {
                return Optional.of(new RuleFinding(Confidence.HIGH, "Both operands are the same expression; this assertion holds regardless of behavior."));
            }
            if (a instanceof ArrayAccessExpr || a instanceof CastExpr) {
                return Optional.of(new RuleFinding(Confidence.MEDIUM, "Both operands are the same expression; this assertion holds regardless of behavior."));
            }
        }
        return Optional.empty();
    }

    private static boolean isBooleanLiteral(Expression e, boolean value) {
        return e instanceof BooleanLiteralExpr ble && ble.getValue() == value;
    }

    private static boolean isPlainConstant(Expression e, String testClassFqn) {
        if (e instanceof EnclosedExpr enc) {
            return isPlainConstant(enc.getInner(), testClassFqn);
        }
        if (e instanceof UnaryExpr un
            && (un.getOperator() == UnaryExpr.Operator.PLUS || un.getOperator() == UnaryExpr.Operator.MINUS)) {
            return isPlainConstant(un.getExpression(), testClassFqn);
        }
        if (e.isBooleanLiteralExpr() || e.isIntegerLiteralExpr() || e.isLongLiteralExpr()
            || e.isDoubleLiteralExpr() || e.isCharLiteralExpr() || e.isStringLiteralExpr()) {
            return true;
        }
        if (e instanceof BinaryExpr be) {
            return FOLDABLE_OPERATORS.contains(be.getOperator())
                && isPlainConstant(be.getLeft(), testClassFqn) && isPlainConstant(be.getRight(), testClassFqn);
        }
        if (e instanceof FieldAccessExpr || e instanceof NameExpr) {
            return isTestClassOwnConstantField(e, testClassFqn);
        }
        return false;
    }

    /**
     * A field resolving to the test class itself (a scaffolding constant the
     * test declares for its own use) counts as plain; a field declared
     * anywhere else - even a nested helper class in the same file - is
     * treated as the code under test's own value (never fires on:
     * "the code under test's own public constants" per the spec).
     */
    private static boolean isTestClassOwnConstantField(Expression e, String testClassFqn) {
        if (testClassFqn == null) {
            return false;
        }
        try {
            ResolvedValueDeclaration resolved = e instanceof NameExpr ne ? ne.resolve() : ((FieldAccessExpr) e).resolve();
            return resolved.isField() && testClassFqn.equals(resolved.asField().declaringType().getQualifiedName());
        } catch (RuntimeException ex) {
            return false; // unresolved - "if constants/operands can't be resolved, the rule stays silent"
        }
    }
}
