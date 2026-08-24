package dev.coverdict.analysis.oracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Walks a test method's body plus its same-compilation-unit private helpers
 * (transitively, docs/rules/README.md's "Helper traversal"), finding every
 * recognized-oracle occurrence and every call visited along the way.
 *
 * <p>Deliberately does not descend into lambda bodies (documented limit,
 * shared by every v0.1 rule): a {@code () -> ...} argument to e.g.
 * {@code assertThrows} is not traversed. A call is never treated as an
 * independent "root call" when it is itself the {@code scope} of an
 * enclosing call - that is a fluent-chain continuation (AssertJ
 * {@code assertThat(x).isEqualTo(y)}), resolved structurally by walking
 * parent pointers rather than by trying to symbol-solve the intermediate
 * link, which no classpath can ever provide (its type is the library's own
 * internal fluent-assertion class).
 */
final class OracleRecognizer {

    private final CompilationUnit cu;
    private final ImportIndex imports;

    OracleRecognizer(CompilationUnit cu) {
        this.cu = cu;
        this.imports = new ImportIndex(cu);
    }

    TraversalResult traverse(MethodDeclaration testMethod) {
        Set<MethodDeclaration> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<OracleOccurrence> oracles = new ArrayList<>();
        List<ResolvedCall> allCalls = new ArrayList<>();
        traverseInto(testMethod, visited, oracles, allCalls);
        return new TraversalResult(oracles, allCalls);
    }

    private void traverseInto(MethodDeclaration method, Set<MethodDeclaration> visited,
                               List<OracleOccurrence> oracles, List<ResolvedCall> allCalls) {
        Optional<BlockStmt> body = method.getBody();
        if (!visited.add(method) || body.isEmpty()) {
            return;
        }
        RootCallCollector collector = new RootCallCollector();
        body.get().accept(collector, null);

        for (MethodCallExpr call : collector.rootCalls) {
            processRootCall(call, visited, oracles, allCalls);
        }
    }

    private void processRootCall(MethodCallExpr call, Set<MethodDeclaration> visited,
                                  List<OracleOccurrence> oracles, List<ResolvedCall> allCalls) {
        CallResolution resolution = resolve(call);
        allCalls.add(new ResolvedCall(call, resolution));
        if (resolution.tier() == CallResolution.Tier.UNRESOLVED) {
            return;
        }
        String fqn = resolution.declaringTypeFqn();
        String name = call.getNameAsString();
        if (OracleAllowlist.isChainAnchor(fqn, name)) {
            if (hasOuterChainedCall(call)) {
                MethodCallExpr terminal = outermostChainCall(call);
                oracles.add(new OracleOccurrence(call, terminal, fqn, terminal.getNameAsString()));
            }
            return;
        }
        if (OracleAllowlist.isUnconditionalOracle(fqn, name)) {
            oracles.add(new OracleOccurrence(call, call, fqn, name));
            return;
        }
        if (resolution.tier() == CallResolution.Tier.SOLVED && resolution.localPrivateDeclaration() != null) {
            traverseInto(resolution.localPrivateDeclaration(), visited, oracles, allCalls);
        }
    }

    private CallResolution resolve(MethodCallExpr call) {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            String fqn = resolved.declaringType().getQualifiedName();
            MethodDeclaration localPrivate = null;
            if (resolved instanceof JavaParserMethodDeclaration jpmd) {
                MethodDeclaration decl = jpmd.getWrappedNode();
                if (decl.isPrivate() && decl.findCompilationUnit().map(u -> u == cu).orElse(false)) {
                    localPrivate = decl;
                }
            }
            return new CallResolution(CallResolution.Tier.SOLVED, fqn, localPrivate);
        } catch (RuntimeException e) {
            // No classpath jar, or genuinely undeclared (fixtures/rules/**/Unresolved.java) -
            // fall back to import-anchoring (K2), which needs no jar at all.
            return importAnchoredOwner(call)
                .map(fqn -> new CallResolution(CallResolution.Tier.IMPORT_ANCHORED, fqn, null))
                .orElse(CallResolution.UNRESOLVED);
        }
    }

    private Optional<String> importAnchoredOwner(MethodCallExpr call) {
        String name = call.getNameAsString();
        Optional<Expression> scopeOpt = call.getScope();
        if (scopeOpt.isEmpty()) {
            String owner = imports.staticSingleImportOwner(name);
            if (owner != null) {
                return Optional.of(owner);
            }
            return imports.wildcardAnchoredOwner(name, OracleAllowlist.ALL_TYPES);
        }
        Expression scope = scopeOpt.get();
        if (scope instanceof MethodCallExpr) {
            return Optional.empty(); // fluent-chain continuation, resolved structurally elsewhere
        }
        String text = scope.toString();
        if (OracleAllowlist.ALL_TYPES.contains(text)) {
            return Optional.of(text); // fully-qualified in source, e.g. org.junit.Assert.assertEquals(...)
        }
        if (scope instanceof NameExpr ne) {
            String owner = imports.typeSingleImportOwner(ne.getNameAsString());
            if (owner != null) {
                return Optional.of(owner);
            }
        }
        return Optional.empty();
    }

    private static boolean hasOuterChainedCall(MethodCallExpr call) {
        return call.getParentNode()
            .filter(p -> p instanceof MethodCallExpr mce && mce.getScope().map(s -> s == call).orElse(false))
            .isPresent();
    }

    private static MethodCallExpr outermostChainCall(MethodCallExpr call) {
        MethodCallExpr current = call;
        Optional<MethodCallExpr> outer = outerChainLink(current);
        while (outer.isPresent()) {
            current = outer.get();
            outer = outerChainLink(current);
        }
        return current;
    }

    private static Optional<MethodCallExpr> outerChainLink(MethodCallExpr call) {
        return call.getParentNode()
            .filter(p -> p instanceof MethodCallExpr mce && mce.getScope().map(s -> s == call).orElse(false))
            .map(p -> (MethodCallExpr) p);
    }

    /** Collects every call that is the top of its own fluent chain - i.e. not itself used as another call's scope. */
    private static final class RootCallCollector extends VoidVisitorAdapter<Void> {
        final List<MethodCallExpr> rootCalls = new ArrayList<>();

        @Override
        public void visit(LambdaExpr n, Void arg) {
            // documented limit: lambda bodies are not traversed (dynamic-test-style
            // deferred code coverdict cannot safely attribute in v0.1)
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            boolean chainContinuation = n.getScope().filter(MethodCallExpr.class::isInstance).isPresent();
            if (!chainContinuation) {
                rootCalls.add(n);
            }
            super.visit(n, arg);
        }
    }
}
