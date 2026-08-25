package dev.coverdict.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;

import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * Three D-24 allowlist entries and one full rule branch that had ZERO
 * fixture or unit-test coverage before this: {@code fixtures/rules/**}
 * exercises JUnit 5, JUnit 4, AssertJ, Truth and Mockito's plain {@code
 * verify}, but never Hamcrest, {@code InOrder}, or {@code BDDMockito}.
 * Found by a self-scan JaCoCo coverage read, not by any spec review - the
 * allowlist branches for these three were dead weight nobody had ever
 * proven actually fire.
 *
 * <p>{@code InOrder.verify(...)} specifically cannot be reached by
 * import-anchoring at all: real usage declares {@code InOrder} as a
 * <em>local variable</em> (`InOrder inOrder = inOrder(mock);`), and {@link
 * OracleRecognizer#fieldTypeOwner} only anchors compilation-unit
 * <em>fields</em> - a local variable's declared type is invisible to every
 * resolution tier except the real Symbol Solver. So this test is the only
 * place that proves {@code InOrder.verify} is recognized <em>at all</em>,
 * and it can only do so with the fixture-harness's real Mockito jar on the
 * solver's path - a real--classpath user is the only one who will ever see
 * this allowlist entry actually fire.
 */
class AllowlistCoverageGapsTest {

    private static final Path HARNESS_DIR = Path.of("target/fixture-harness");

    @TempDir
    Path repoRoot;

    private ModuleDefinition module() {
        return new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));
    }

    private Path testDir() throws IOException {
        Path dir = repoRoot.resolve("src/test/java/com/example");
        Files.createDirectories(dir);
        return dir;
    }

    private static List<TypeSolver> mockitoAndHamcrestJars() {
        try {
            return List.of(
                new JarTypeSolver(HARNESS_DIR.resolve("mockito-core.jar")),
                new JarTypeSolver(HARNESS_DIR.resolve("hamcrest.jar")));
        } catch (IOException e) {
            throw new IllegalStateException("fixture-harness jars missing - run 'mvn generate-test-resources' first", e);
        }
    }

    private OracleScanResult scan() {
        return OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null,
            OracleScanOptions.defaults().withTypeSolvers(mockitoAndHamcrestJars()));
    }

    // --- Hamcrest MatcherAssert.assertThat (OracleAllowlist HAMCREST_MATCHER_ASSERT) ---

    @Test
    void hamcrestMatcherAssertIsRecognizedAsAnOracle() throws IOException {
        Files.writeString(testDir().resolve("HamcrestTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.hamcrest.MatcherAssert.assertThat;",
            "import static org.hamcrest.Matchers.equalTo;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class HamcrestTest {",
            "    @Test",
            "    void usesHamcrestAssertThat() {",
            "        assertThat(2 + 2, equalTo(4));",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    // --- Mockito InOrder.verify (OracleAllowlist MOCKITO_IN_ORDER) ---

    @Test
    void mockitoInOrderVerifyIsRecognizedAsAnOracle() throws IOException {
        Files.writeString(testDir().resolve("OrderedTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.mockito.Mockito.inOrder;",
            "import static org.mockito.Mockito.mock;",
            "",
            "import java.util.List;",
            "",
            "import org.junit.jupiter.api.Test;",
            "import org.mockito.InOrder;",
            "",
            "class OrderedTest {",
            "    @Test",
            "    void verifiesCallOrder() {",
            "        List<String> first = mock(List.class);",
            "        List<String> second = mock(List.class);",
            "        first.add(\"a\");",
            "        second.add(\"b\");",
            "        InOrder inOrder = inOrder(first, second);",
            "        inOrder.verify(first).add(\"a\");",
            "        inOrder.verify(second).add(\"b\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();
        assertTrue(result.findings().isEmpty(), result.findings().toString());
    }

    /**
     * The negative control for the doc comment's claim above: with no jars at
     * all, {@code inOrder.verify(...)} is a local-variable-scoped call that no
     * resolution tier can anchor, so the test IS reported as oracle-less. This
     * is today's real, classpath-less default - not a defect this change is
     * fixing, just the boundary the jar-backed test above sits on the other
     * side of.
     */
    @Test
    void withoutAClasspathInOrderVerifyIsNotRecognized() throws IOException {
        Files.writeString(testDir().resolve("OrderedTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.mockito.Mockito.inOrder;",
            "import static org.mockito.Mockito.mock;",
            "",
            "import java.util.List;",
            "",
            "import org.junit.jupiter.api.Test;",
            "import org.mockito.InOrder;",
            "",
            "class OrderedTest {",
            "    @Test",
            "    void verifiesCallOrder() {",
            "        List<String> first = mock(List.class);",
            "        first.add(\"a\");",
            "        InOrder inOrder = inOrder(first);",
            "        inOrder.verify(first).add(\"a\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("NO_RECOGNIZED_ORACLE", result.findings().get(0).rule());
    }

    // --- Mockito BDDMockito.then (OracleAllowlist MOCKITO_BDD, a chain anchor) ---

    @Test
    void bddMockitoThenIsRecognizedAsAnOracle() throws IOException {
        Files.writeString(testDir().resolve("BddTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.mockito.BDDMockito.then;",
            "import static org.mockito.Mockito.mock;",
            "",
            "import java.util.List;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class BddTest {",
            "    @Test",
            "    void verifiesViaBdd() {",
            "        List<String> collaborator = mock(List.class);",
            "        collaborator.add(\"x\");",
            "        then(collaborator).should().add(\"x\");",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    // --- NullCheckOnlyRule's Hamcrest branch (isHamcrestNotNullCheck / isNotNullMatcher) ---

    /**
     * `assertThat(x, notNullValue())` - the direct matcher shape, not just
     * "recognized as some oracle" but specifically caught by NULL_CHECK_ONLY
     * (an oracle that only verifies non-nullness is INFO-severity, not silence
     * - docs/rules/NULL_CHECK_ONLY.md). Nothing exercised this rule's own
     * Hamcrest recursion before: {@code isHamcrestNotNullCheck} and {@code
     * isNotNullMatcher} were 0% covered.
     */
    @Test
    void hamcrestNotNullValueMatcherTriggersNullCheckOnly() throws IOException {
        Files.writeString(testDir().resolve("NotNullTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.hamcrest.MatcherAssert.assertThat;",
            "import static org.hamcrest.Matchers.notNullValue;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class NotNullTest {",
            "    @Test",
            "    void checksOnlyThatItIsNotNull() {",
            "        assertThat(new Object(), notNullValue());",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("NULL_CHECK_ONLY", result.findings().get(0).rule());
    }

    /**
     * `assertThat(x, is(notNullValue()))` - the {@code isNotNullMatcher}
     * recursive case (`is(...)` wrapping the real matcher), a distinct code
     * path from the direct form above.
     */
    @Test
    void hamcrestIsNotNullValueWrapperAlsoTriggersNullCheckOnly() throws IOException {
        Files.writeString(testDir().resolve("NotNullWrappedTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.hamcrest.MatcherAssert.assertThat;",
            "import static org.hamcrest.Matchers.is;",
            "import static org.hamcrest.Matchers.notNullValue;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class NotNullWrappedTest {",
            "    @Test",
            "    void checksOnlyThatItIsNotNull() {",
            "        assertThat(new Object(), is(notNullValue()));",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("NULL_CHECK_ONLY", result.findings().get(0).rule());
    }

    /**
     * A real assertion (not just a null check) using a different Hamcrest
     * matcher through the same `is(...)` wrapper must NOT be mistaken for
     * NULL_CHECK_ONLY - proves {@code isNotNullMatcher}'s recursion actually
     * inspects the wrapped matcher's name rather than firing on any `is(...)`.
     */
    @Test
    void hamcrestIsWrappingADifferentMatcherIsNotANullCheck() throws IOException {
        Files.writeString(testDir().resolve("RealCheckTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.hamcrest.MatcherAssert.assertThat;",
            "import static org.hamcrest.Matchers.equalTo;",
            "import static org.hamcrest.Matchers.is;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class RealCheckTest {",
            "    @Test",
            "    void checksARealValue() {",
            "        assertThat(2 + 2, is(equalTo(4)));",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }
}
