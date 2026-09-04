package dev.proofjava.analysis.oracle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import dev.proofjava.analysis.model.RuleIds;
import dev.proofjava.analysis.model.Confidence;
import dev.proofjava.analysis.model.Severity;

/** docs/rules/CATCH_ORACLE_WITHOUT_FAIL.md */
final class CatchOracleWithoutFailRule {

    static final String RULE_ID = RuleIds.CATCH_ORACLE_WITHOUT_FAIL;
    static final Severity SEVERITY = Severity.WARNING;
    static final String SUGGESTED_ACTION =
        "An exception thrown here passes silently; add fail() after the invocation inside try, "
            + "use assertThrows/assertDoesNotThrow, or let the exception propagate.";

    private CatchOracleWithoutFailRule() {
    }

    static Optional<RuleFinding> evaluate(MethodDeclaration testMethod, TraversalResult traversal) {
        Optional<BlockStmt> body = testMethod.getBody();
        if (body.isEmpty()) {
            return Optional.empty();
        }
        for (TryStmt tryStmt : findTryStatements(body.get())) {
            Optional<RuleFinding> finding = evaluateTry(tryStmt, traversal);
            if (finding.isPresent()) {
                return finding;
            }
        }
        return Optional.empty();
    }

    private static Optional<RuleFinding> evaluateTry(TryStmt tryStmt, TraversalResult traversal) {
        if (tryStmt.getCatchClauses().isEmpty()) {
            return Optional.empty(); // try-with-resources without a catch clause never fires
        }
        Range tryRange = tryStmt.getTryBlock().getRange().orElse(null);
        if (tryRange == null || !hasAnyRootCall(tryStmt.getTryBlock())) {
            return Optional.empty(); // condition 1: the try block must invoke at least one method
        }
        boolean allOraclesInsideTry = traversal.oracles().stream()
            .allMatch(o -> withinRange(o.anchorCall(), tryRange));
        if (!allOraclesInsideTry) {
            return Optional.empty(); // an oracle exists outside this try (e.g. after the whole statement) - never fires
        }
        boolean tryCallsFail = traversal.oracles().stream()
            .anyMatch(o -> "fail".equals(o.methodName()) && withinRange(o.anchorCall(), tryRange));
        if (tryCallsFail) {
            // The classic manual "expect this exception" idiom: invoke the code under
            // test, then call fail() right after it, then catch the specific expected
            // exception with an empty body. fail() is reached, and only reached, when
            // the expected exception did NOT occur, so being unreached on the exception
            // path is itself the verification, not a silently skipped oracle.
            return Optional.empty();
        }

        List<RuleFinding> catchFindings = new ArrayList<>();
        for (CatchClause clause : tryStmt.getCatchClauses()) {
            Optional<RuleFinding> finding = evaluateCatch(clause.getBody(), traversal);
            if (finding.isEmpty()) {
                return Optional.empty(); // this catch clause has an oracle/rethrow - the whole try is exonerated
            }
            catchFindings.add(finding.get());
        }
        return catchFindings.stream().min((a, b) -> rank(a.confidence()) - rank(b.confidence()));
    }

    /** @return empty when this catch clause exonerates the try (has an oracle, rethrows). */
    private static Optional<RuleFinding> evaluateCatch(BlockStmt catchBody, TraversalResult traversal) {
        Range catchRange = catchBody.getRange().orElse(null);
        if (catchRange == null) {
            return Optional.empty();
        }
        boolean catchHasOracle = traversal.oracles().stream().anyMatch(o -> withinRange(o.anchorCall(), catchRange));
        boolean catchHasRethrow = !catchBody.findAll(ThrowStmt.class).isEmpty();
        if (catchHasOracle || catchHasRethrow) {
            return Optional.empty();
        }

        Optional<MethodCallExpr> suggestiveUnresolved = traversal.unresolvedCalls()
            .filter(c -> withinRange(c, catchRange) && OracleAllowlist.isOracleSuggestiveName(c.getNameAsString()))
            .findFirst();
        if (suggestiveUnresolved.isPresent()) {
            return Optional.of(new RuleFinding(Confidence.INCONCLUSIVE,
                "Catch block calls an unresolved '" + suggestiveUnresolved.get().getNameAsString()
                    + "' that looks like it could be a custom fail helper."));
        }
        boolean anyUnresolvedInCatch = traversal.unresolvedCalls().anyMatch(c -> withinRange(c, catchRange));
        if (anyUnresolvedInCatch) {
            return Optional.of(new RuleFinding(Confidence.INCONCLUSIVE, "Catch block contains a call that could not be resolved."));
        }
        // Only the top-level action per statement matters here, not calls nested inside its
        // arguments (e.g. e.getMessage() feeding a println is not a second, non-logging action).
        List<MethodCallExpr> topLevelCalls = topLevelStatementCalls(catchBody);
        boolean onlyLogging = catchBody.getStatements().isEmpty()
            || (topLevelCalls.size() == catchBody.getStatements().size()
                && topLevelCalls.stream().allMatch(CatchOracleWithoutFailRule::isLoggingCall));
        if (onlyLogging) {
            return Optional.of(new RuleFinding(Confidence.HIGH,
                "An exception thrown in the try block is caught and swallowed without any assertion."));
        }
        return Optional.of(new RuleFinding(Confidence.MEDIUM,
            "An exception thrown in the try block is caught without any assertion, though the catch block does something."));
    }

    private static int rank(Confidence c) {
        return switch (c) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case INCONCLUSIVE -> 3;
        };
    }

    private static boolean isLoggingCall(MethodCallExpr call) {
        try {
            var resolved = call.resolve();
            String fqn = resolved.declaringType().getQualifiedName();
            String name = call.getNameAsString();
            return "java.io.PrintStream".equals(fqn) && ("println".equals(name) || "print".equals(name));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean withinRange(com.github.javaparser.ast.Node node, Range outer) {
        return node.getRange().map(outer::contains).orElse(false);
    }

    private static List<MethodCallExpr> topLevelStatementCalls(BlockStmt block) {
        List<MethodCallExpr> calls = new ArrayList<>();
        for (Statement stmt : block.getStatements()) {
            if (stmt instanceof ExpressionStmt exprStmt && exprStmt.getExpression() instanceof MethodCallExpr call) {
                calls.add(call);
            }
        }
        return calls;
    }

    private static boolean hasAnyRootCall(BlockStmt block) {
        return !rootCallsIn(block).isEmpty();
    }

    private static List<MethodCallExpr> rootCallsIn(BlockStmt block) {
        RootCallOnlyCollector collector = new RootCallOnlyCollector();
        block.accept(collector, null);
        return collector.calls;
    }

    private static List<TryStmt> findTryStatements(BlockStmt methodBody) {
        TryStmtCollector collector = new TryStmtCollector();
        methodBody.accept(collector, null);
        return collector.tryStatements;
    }

    private static final class RootCallOnlyCollector extends VoidVisitorAdapter<Void> {
        final List<MethodCallExpr> calls = new ArrayList<>();

        @Override
        public void visit(LambdaExpr n, Void arg) {
            // documented limit shared with OracleRecognizer: lambda bodies are not traversed
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            boolean chainContinuation = n.getScope().filter(MethodCallExpr.class::isInstance).isPresent();
            if (!chainContinuation) {
                calls.add(n);
            }
            super.visit(n, arg);
        }
    }

    private static final class TryStmtCollector extends VoidVisitorAdapter<Void> {
        final List<TryStmt> tryStatements = new ArrayList<>();

        @Override
        public void visit(LambdaExpr n, Void arg) {
            // same documented limit as RootCallOnlyCollector: lambda bodies are not traversed
        }

        @Override
        public void visit(TryStmt n, Void arg) {
            tryStatements.add(n);
            super.visit(n, arg);
        }
    }
}
