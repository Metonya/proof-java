package dev.coverdict.analysis.oracle;

import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * How a single call was resolved (K2 in the M1b plan): {@code SOLVED} means
 * JavaParser's Symbol Solver found a declaration (works for JDK calls
 * always, and for same-compilation-unit user types without any jar);
 * {@code IMPORT_ANCHORED} means the solver failed but the call's simple name
 * is anchored to exactly one type by the file's own import declarations -
 * Java's own naming rules make this certain without needing the type on the
 * classpath; {@code UNRESOLVED} means neither.
 */
record CallResolution(Tier tier, String declaringTypeFqn, MethodDeclaration localPrivateDeclaration) {

    enum Tier { SOLVED, IMPORT_ANCHORED, UNRESOLVED }

    static final CallResolution UNRESOLVED = new CallResolution(Tier.UNRESOLVED, null, null);
}
