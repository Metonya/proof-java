package dev.coverdict.analysis.oracle;

import java.util.List;
import java.util.stream.Stream;

import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * Every oracle occurrence found while walking a test method plus its
 * same-compilation-unit private helpers (transitively), and every "root"
 * call visited along the way with how it resolved - the raw material each
 * of the four rules derives its own confidence and evidence from.
 */
record TraversalResult(List<OracleOccurrence> oracles, List<ResolvedCall> allCalls) {

    Stream<MethodCallExpr> unresolvedCalls() {
        return allCalls.stream()
            .filter(rc -> rc.resolution().tier() == CallResolution.Tier.UNRESOLVED)
            .map(ResolvedCall::call);
    }

    boolean anyUnresolvedSuggestive() {
        return unresolvedCalls().anyMatch(c -> OracleAllowlist.isOracleSuggestiveName(c.getNameAsString()));
    }

    boolean anyUnresolvedNonSuggestive() {
        return unresolvedCalls().anyMatch(c -> !OracleAllowlist.isOracleSuggestiveName(c.getNameAsString()));
    }
}
