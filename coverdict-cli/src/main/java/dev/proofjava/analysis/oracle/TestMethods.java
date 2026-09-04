package dev.proofjava.analysis.oracle;

import java.util.Set;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

/**
 * Test method recognition (docs/rules/README.md): JUnit 5's five test
 * annotations plus JUnit 4's {@code org.junit.Test} (D-24), matched by
 * simple annotation name since a call site may spell it either
 * {@code @Test} (imported) or {@code @org.junit.Test} (fully qualified) -
 * both resolve to the same last path segment.
 */
final class TestMethods {

    private static final Set<String> TEST_ANNOTATIONS =
        Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");

    /** JUnit 5's {@code @Disabled} and JUnit 4's {@code @Ignore}, matched by simple name like the rest. */
    private static final Set<String> DISABLED_ANNOTATIONS = Set.of("Disabled", "Ignore");

    private TestMethods() {
    }

    static boolean isTestMethod(MethodDeclaration method) {
        return method.getAnnotations().stream().anyMatch(a -> TEST_ANNOTATIONS.contains(simpleName(a)));
    }

    /**
     * docs/rules/README.md: a disabled test is "still analyzed but findings
     * carry a {@code disabled test} note in the message". Analyzing it is the
     * deliberate part - a disabled test with no oracle is still a finding a
     * reader should see, and silently skipping it would quietly shrink the
     * denominator (hard rule 3a). The class-level annotation counts too: it
     * disables every method in the class just as effectively.
     */
    static boolean isDisabled(MethodDeclaration method) {
        if (method.getAnnotations().stream().anyMatch(a -> DISABLED_ANNOTATIONS.contains(simpleName(a)))) {
            return true;
        }
        return method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
            .map(c -> c.getAnnotations().stream().anyMatch(a -> DISABLED_ANNOTATIONS.contains(simpleName(a))))
            .orElse(false);
    }

    static boolean isTestFactory(MethodDeclaration method) {
        return method.getAnnotations().stream().anyMatch(a -> "TestFactory".equals(simpleName(a)));
    }

    /**
     * JUnit 4's {@code @Test(expected = SomeException.class)} is itself a
     * complete oracle (NO_RECOGNIZED_ORACLE fires only when "no
     * {@code @Test(expected=...)}" holds) - not a method call, so it needs
     * its own check outside the normal call traversal. {@code Test.None}
     * (the JUnit 4 default) does not count as "expects an exception".
     */
    static boolean hasExpectedExceptionAnnotation(MethodDeclaration method) {
        for (AnnotationExpr a : method.getAnnotations()) {
            if (!"Test".equals(simpleName(a)) || !(a instanceof NormalAnnotationExpr normal)) {
                continue;
            }
            for (MemberValuePair pair : normal.getPairs()) {
                if ("expected".equals(pair.getNameAsString()) && !pair.getValue().toString().contains("None")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String simpleName(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? name : name.substring(lastDot + 1);
    }
}
