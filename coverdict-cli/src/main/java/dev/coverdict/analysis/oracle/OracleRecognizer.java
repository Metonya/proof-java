package dev.coverdict.analysis.oracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
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
    private final CustomOracles customOracles;
    private final Set<String> anchorableTypes;

    OracleRecognizer(CompilationUnit cu) {
        this(cu, CustomOracles.none());
    }

    OracleRecognizer(CompilationUnit cu, CustomOracles customOracles) {
        this.cu = cu;
        this.imports = new ImportIndex(cu);
        this.customOracles = customOracles;
        if (customOracles.isEmpty()) {
            this.anchorableTypes = OracleAllowlist.ALL_TYPES;
        } else {
            Set<String> union = new java.util.LinkedHashSet<>(OracleAllowlist.ALL_TYPES);
            union.addAll(customOracles.types());
            this.anchorableTypes = Set.copyOf(union);
        }
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
        if (OracleAllowlist.isUnconditionalOracle(fqn, name) || customOracles.matches(fqn, name)) {
            oracles.add(new OracleOccurrence(call, call, fqn, name));
            return;
        }
        if (resolution.tier() == CallResolution.Tier.SOLVED && resolution.localHelperDeclaration() != null) {
            traverseInto(resolution.localHelperDeclaration(), visited, oracles, allCalls);
        }
    }

    private CallResolution resolve(MethodCallExpr call) {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            String fqn = resolved.declaringType().getQualifiedName();
            MethodDeclaration localHelper = null;
            if (resolved instanceof JavaParserMethodDeclaration jpmd) {
                MethodDeclaration decl = jpmd.getWrappedNode();
                if (isLocalHelperCandidate(decl)) {
                    localHelper = decl;
                }
            }
            return new CallResolution(CallResolution.Tier.SOLVED, fqn, localHelper);
        } catch (RuntimeException e) {
            // Symbol Solver can fail here even for a genuinely local, well-formed call: it
            // must resolve the callee's own declaration to report a result, and that fails
            // whenever any of the callee's parameter types is itself unresolvable (e.g. an
            // external library type with no jar/--classpath configured) - independent of
            // whether the call site's own argument is a lambda. Before giving up, try the
            // file's own imports (K2, no jar needed), then - for a bare, unqualified call
            // only - a same-compilation-unit name match (D-33): if exactly one private or
            // static method here has this name, it is that call's target by Java's own
            // shadowing rules, and ambiguity (more than one match) safely disables this path.
            return importAnchoredOwner(call)
                .map(fqn -> new CallResolution(CallResolution.Tier.IMPORT_ANCHORED, fqn, null))
                .or(() -> sameFileNameMatch(call))
                .orElse(CallResolution.UNRESOLVED);
        }
    }

    private Optional<CallResolution> sameFileNameMatch(MethodCallExpr call) {
        if (call.getScope().isPresent()) {
            return Optional.empty(); // qualified call - a same-file name match here would be a guess, not a fact
        }
        List<MethodDeclaration> matches = cu.findAll(MethodDeclaration.class).stream()
            .filter(this::isLocalHelperCandidate)
            .filter(decl -> decl.getNameAsString().equals(call.getNameAsString()))
            .toList();
        if (matches.size() != 1) {
            return Optional.empty(); // no candidate, or a real overload - never guess between them
        }
        String fqn = cu.getPrimaryType().flatMap(t -> t.getFullyQualifiedName()).orElse(matches.get(0).getNameAsString());
        return Optional.of(new CallResolution(CallResolution.Tier.SOLVED, fqn, matches.get(0)));
    }

    /** Same-compilation-unit and private-or-static: the two hallmarks of a genuine test-support helper (D-33). */
    private boolean isLocalHelperCandidate(MethodDeclaration decl) {
        return (decl.isPrivate() || decl.isStatic()) && decl.findCompilationUnit().map(u -> u == cu).orElse(false);
    }

    private Optional<String> importAnchoredOwner(MethodCallExpr call) {
        String name = call.getNameAsString();
        Optional<Expression> scopeOpt = call.getScope();
        if (scopeOpt.isEmpty()) {
            String owner = imports.staticSingleImportOwner(name);
            if (owner != null) {
                return Optional.of(owner);
            }
            return imports.wildcardAnchoredOwner(name, anchorableTypes);
        }
        Expression scope = scopeOpt.get();
        if (scope instanceof MethodCallExpr) {
            return Optional.empty(); // fluent-chain continuation, resolved structurally elsewhere
        }
        String text = scope.toString();
        if (anchorableTypes.contains(text)) {
            return Optional.of(text); // fully-qualified in source, e.g. org.junit.Assert.assertEquals(...)
        }
        if (scope instanceof NameExpr ne) {
            String owner = imports.typeSingleImportOwner(ne.getNameAsString());
            if (owner != null) {
                return Optional.of(owner);
            }
            return fieldTypeOwner(ne);
        }
        return Optional.empty();
    }

    /**
     * The scope names a <em>variable</em>, not a type - the shape JUnit 4's
     * {@code ExpectedException} rule always takes:
     *
     * <pre>{@code @Rule public ExpectedException thrown = ExpectedException.none();
     * ...
     * thrown.expect(IllegalArgumentException.class);}</pre>
     *
     * <p>Every other anchoring path fails here: {@code thrown} is not a type
     * name, so {@link ImportIndex#typeSingleImportOwner} misses, and with no
     * {@code --classpath} the Symbol Solver cannot resolve the library call
     * either. So look the name up as a field of this compilation unit and
     * anchor <em>its declared type</em> through the imports instead.
     *
     * <p>Deliberately narrow: fields of this compilation unit only, and only
     * when the declared type's simple name resolves through a real import to a
     * recognized type. An inherited field, or a type that is not imported by
     * name, stays unresolved rather than being guessed at (D-17).
     */
    private Optional<String> fieldTypeOwner(NameExpr scope) {
        String variableName = scope.getNameAsString();
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (!variable.getNameAsString().equals(variableName)) {
                    continue;
                }
                String declaredType = variable.getType().asString();
                if (anchorableTypes.contains(declaredType)) {
                    return Optional.of(declaredType); // declared fully qualified in source
                }
                String owner = imports.typeSingleImportOwner(declaredType);
                if (owner != null) {
                    return Optional.of(owner);
                }
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
