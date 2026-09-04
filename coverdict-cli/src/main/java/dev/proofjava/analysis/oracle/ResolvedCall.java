package dev.proofjava.analysis.oracle;

import com.github.javaparser.ast.expr.MethodCallExpr;

/** One "root" call visited during a traversal (see {@link OracleRecognizer}), paired with how it resolved. */
record ResolvedCall(MethodCallExpr call, CallResolution resolution) {
}
