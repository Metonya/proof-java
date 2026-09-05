package dev.proofjava.analysis.oracle;

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

    /**
     * D-90: calls that resolved to a real method whose name reads like an
     * assertion, but which is not on the recognized-oracle allowlist - almost
     * always a project's own test helper in another file (D-17 keeps those out
     * of scope until configured).
     *
     * <p>The finding used to say only "no recognized assertion", leaving the
     * reader to work out which helper to allowlist. Measured on google/gson,
     * this was 13 of 33 findings, every one of them a real oracle in
     * {@code MoreAsserts} or {@code DefaultTypeAdaptersTest}. Naming the call
     * turns a dead end into a config line the reader can copy.
     *
     * @return {@code fully.qualified.Type#methodName} for each, most specific
     *         first, without duplicates.
     */
    Stream<String> resolvedSuggestiveNonOracleCalls() {
        return allCalls.stream()
            .filter(rc -> rc.resolution().tier() != CallResolution.Tier.UNRESOLVED)
            .filter(rc -> rc.resolution().declaringTypeFqn() != null)
            .filter(rc -> OracleAllowlist.isOracleSuggestiveName(rc.call().getNameAsString()))
            .map(rc -> rc.resolution().declaringTypeFqn() + "#" + rc.call().getNameAsString())
            .distinct();
    }
}
