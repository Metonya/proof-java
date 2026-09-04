package dev.proofjava.analysis.oracle;

import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * One recognized oracle call in a test method's traversal. For a direct
 * call (JUnit/Mockito/Hamcrest) {@code anchorCall} and {@code terminalCall}
 * are the same node and {@code methodName} is that call's own name. For an
 * AssertJ/BDD fluent chain, {@code anchorCall} is the {@code assertThat}
 * (or {@code then}) call and {@code terminalCall}/{@code methodName} are the
 * outermost call in the chain - e.g. {@code isNotNull} in
 * {@code assertThat(x).isNotNull()} - since that is the call whose name
 * actually decides what was verified (NULL_CHECK_ONLY needs exactly this).
 */
record OracleOccurrence(MethodCallExpr anchorCall, MethodCallExpr terminalCall, String declaringTypeFqn, String methodName) {
}
