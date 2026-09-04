package dev.proofjava.analysis.oracle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * Resolves the compile-time-certain half of oracle recognition (K2 in the
 * M1b plan): given a bare method name or a simple type name used as a call's
 * scope, which fully-qualified type does Java's own import rules say it
 * names? This needs no jar on the classpath - it is a direct reading of the
 * file's own import declarations, valid whether or not Symbol Solver could
 * resolve the call (real user runs have no --classpath yet).
 */
final class ImportIndex {

    private final Map<String, String> staticSingleImports = new HashMap<>();
    private final List<String> staticWildcardImports = new java.util.ArrayList<>();
    private final Map<String, String> typeSingleImports = new HashMap<>();
    private final Set<String> locallyDeclaredMethodNames = new HashSet<>();

    ImportIndex(CompilationUnit cu) {
        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            int lastDot = name.lastIndexOf('.');
            if (imp.isStatic() && imp.isAsterisk()) {
                staticWildcardImports.add(name);
            } else if (imp.isStatic()) {
                if (lastDot >= 0) {
                    staticSingleImports.put(name.substring(lastDot + 1), name.substring(0, lastDot));
                }
            } else if (!imp.isAsterisk()) {
                String simple = lastDot < 0 ? name : name.substring(lastDot + 1);
                typeSingleImports.put(simple, name);
            }
        }
        cu.findAll(MethodDeclaration.class).forEach(m -> locallyDeclaredMethodNames.add(m.getNameAsString()));
    }

    /** For a bare (unqualified) call name, the type a single static import names it as - null if none. */
    String staticSingleImportOwner(String methodName) {
        return staticSingleImports.get(methodName);
    }

    /**
     * @return the declaring type of every on-demand static import, when it is
     * safe to treat any of them as the caller's owner: every one is on
     * {@code allowlistTypes}, and no method of this name is declared anywhere
     * in this file (which would shadow an on-demand import under Java's own
     * resolution rules).
     */
    Optional<String> wildcardAnchoredOwner(String methodName, Set<String> allowlistTypes) {
        if (staticWildcardImports.isEmpty() || locallyDeclaredMethodNames.contains(methodName)) {
            return Optional.empty();
        }
        for (String owner : staticWildcardImports) {
            if (!allowlistTypes.contains(owner)) {
                return Optional.empty();
            }
        }
        return Optional.of(staticWildcardImports.get(0));
    }

    /** For a simple type name used as a call's scope, the FQN a single-type import names it as - null if none. */
    String typeSingleImportOwner(String simpleTypeName) {
        return typeSingleImports.get(simpleTypeName);
    }
}
