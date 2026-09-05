package dev.proofjava.analysis.oracle;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
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

import dev.proofjava.analysis.model.RuleIds;
import dev.proofjava.analysis.model.Confidence;
import dev.proofjava.analysis.model.Severity;

/**
 * docs/rules/TAUTOLOGICAL_ORACLE.md. Only inspects direct JUnit-style calls
 * (assertEquals/assertTrue/assertFalse/assertNotNull) - every closed-list
 * pattern example in the spec is this shape; AssertJ fluent-chain argument
 * inspection is not attempted (out of scope this step).
 */
final class TautologicalOracleRule {

    static final String RULE_ID = RuleIds.TAUTOLOGICAL_ORACLE;
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
        boolean selfComparisonSuppressed = suppressesEqualsWithItself(testMethod);

        for (OracleOccurrence occ : traversal.oracles()) {
            // Fluent-chain argument shapes are not inspected (see class javadoc); only a
            // direct JUnit-style call (anchor == terminal) is a candidate for these patterns.
            boolean isDirectJunitCall = occ.anchorCall() == occ.terminalCall()
                && (OracleAllowlist.JUNIT5_ASSERTIONS.equals(occ.declaringTypeFqn())
                    || OracleAllowlist.JUNIT4_ASSERT.equals(occ.declaringTypeFqn()));
            if (isDirectJunitCall) {
                Optional<RuleFinding> finding = matchPattern(occ.methodName(), occ.anchorCall(), testClassFqn,
                    selfComparisonSuppressed);
                if (finding.isPresent()) {
                    return finding;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Self-comparison (pattern 3) has one well-established legitimate use:
     * verifying an {@code equals()} implementation's reflexivity, part of
     * the {@code Object.equals} contract - {@code assertEquals(x, x)} is
     * exactly the right call, not a copy-paste mistake, when that is the
     * point of the test. Found on commons-io's own {@code ByteOrderMarkTest}
     * (real repo, real false positive): {@code @SuppressWarnings
     * ("EqualsWithItself")} directly above the method is IDE/static-analysis
     * convention for exactly this - an author who did not mean the
     * comparison suppresses a different, unrelated inspection, not this
     * one. Checked on the test method first (the common case, right next to
     * the assertion) and its enclosing class (a class-level suppression
     * covers every method in it). The other three patterns are unaffected -
     * this suppression is specific to "the same expression compared to
     * itself," not "any tautological assertion in this test."
     */
    private static boolean suppressesEqualsWithItself(MethodDeclaration testMethod) {
        if (hasEqualsWithItselfSuppression(testMethod.getAnnotations())) {
            return true;
        }
        return testMethod.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(type -> hasEqualsWithItselfSuppression(type.getAnnotations()))
            .orElse(false);
    }

    private static boolean hasEqualsWithItselfSuppression(List<AnnotationExpr> annotations) {
        return annotations.stream()
            .filter(a -> "SuppressWarnings".equals(a.getNameAsString()))
            .anyMatch(a -> a.toString().contains("EqualsWithItself"));
    }

    private static Optional<RuleFinding> matchPattern(String methodName, MethodCallExpr call, String testClassFqn,
                                                        boolean selfComparisonSuppressed) {
        List<Expression> args = call.getArguments();
        if ("assertEquals".equals(methodName) && args.size() >= 2) {
            Optional<RuleFinding> f = matchAssertEquals(args.get(0), args.get(1), testClassFqn, selfComparisonSuppressed);
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

    private static Optional<RuleFinding> matchAssertEquals(Expression a, Expression b, String testClassFqn,
                                                             boolean selfComparisonSuppressed) {
        // Checked before pattern 1: a syntactic self-comparison (D-98's real
        // shape, ByteOrderMark.UTF_16BE compared to itself) can *also* satisfy
        // isPlainConstant's own "resolves to a field" branch depending on how
        // the symbol solver classifies it - the suppression must win over
        // either pattern for the exact same expression, not just pattern 3's
        // own wording of it. A pattern-1 case that is not a self-comparison
        // (two genuinely different constants) is untouched below.
        boolean isSelfComparison = a.toString().equals(b.toString())
            && a.findAll(MethodCallExpr.class).isEmpty() && b.findAll(MethodCallExpr.class).isEmpty()
            && (a instanceof NameExpr || a instanceof FieldAccessExpr || a instanceof ArrayAccessExpr || a instanceof CastExpr);
        if (isSelfComparison) {
            if (selfComparisonSuppressed) {
                return Optional.empty();
            }
            Confidence confidence = (a instanceof NameExpr || a instanceof FieldAccessExpr) ? Confidence.HIGH : Confidence.MEDIUM;
            return Optional.of(new RuleFinding(confidence, "Both operands are the same expression; this assertion holds regardless of behavior."));
        }
        if (isPlainConstant(a, testClassFqn) && isPlainConstant(b, testClassFqn)) {
            return Optional.of(new RuleFinding(Confidence.HIGH, "Both operands are compile-time constants; this assertion holds for any implementation."));
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
