package dev.proofjava.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * D-98: found on apache/commons-io's real test suite (a genuine false
 * positive, not a target-repo defect). {@code assertEquals(x, x)} is the
 * standard, deliberate way to verify an {@code equals()} implementation's
 * reflexivity (part of the {@code Object.equals} contract) - commons-io's
 * {@code ByteOrderMarkTest.testEquals} does exactly this
 * ({@code assertEquals(ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16BE)}),
 * marked with {@code @SuppressWarnings("EqualsWithItself")} to silence the
 * same observation from IDE/static-analysis tooling. TAUTOLOGICAL_ORACLE's
 * self-comparison pattern (docs/rules/TAUTOLOGICAL_ORACLE.md pattern 3) had
 * no carve-out for this - a real, established Java testing idiom, not a
 * copy-paste mistake.
 *
 * <p>Each fixture compares a field of the class *under test* (a static
 * constant on {@code ByteOrderMark}, an unrelated declaring type from the
 * test class's own perspective) to itself - not a field the test class
 * declares on itself, which the rule's own "scaffolding constant" carve-out
 * (isTestClassOwnConstantField) already treats as pattern 1
 * (constant-vs-constant), a different branch this fix does not touch.
 */
class TautologicalOracleEqualsReflexivityTest {

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

    private void writeByteOrderMark(Path dir) throws IOException {
        Files.writeString(dir.resolve("ByteOrderMark.java"), String.join("\n",
            "package com.example;",
            "",
            "public final class ByteOrderMark {",
            "    public static final ByteOrderMark UTF_16BE = new ByteOrderMark();",
            "}",
            ""));
    }

    private OracleScanResult scan() {
        return OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);
    }

    @Test
    void selfComparisonWithoutSuppressionStillFires() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ByteOrderMarkTest {",
            "    @Test",
            "    void testEquals() {",
            "        assertEquals(ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16BE);",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("TAUTOLOGICAL_ORACLE", result.findings().get(0).rule());
    }

    @Test
    void aSuppressWarningsEqualsWithItselfOnTheMethodSilencesSelfComparison() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ByteOrderMarkTest {",
            "    @SuppressWarnings(\"EqualsWithItself\")",
            "    @Test",
            "    void testEquals() {",
            "        assertEquals(ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16BE);",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    @Test
    void aClassLevelSuppressWarningsEqualsWithItselfAlsoSilencesIt() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "@SuppressWarnings(\"EqualsWithItself\")",
            "class ByteOrderMarkTest {",
            "    @Test",
            "    void testEquals() {",
            "        assertEquals(ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16BE);",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    /** A different, unrelated suppression must not become a blanket escape hatch for every tautology pattern. */
    @Test
    void anUnrelatedSuppressWarningsDoesNotSilenceSelfComparison() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ByteOrderMarkTest {",
            "    @SuppressWarnings(\"unchecked\")",
            "    @Test",
            "    void testEquals() {",
            "        assertEquals(ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16BE);",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("TAUTOLOGICAL_ORACLE", result.findings().get(0).rule());
    }

    /**
     * The second, unannotated legitimate shape found on the same repo:
     * commons-io's {@code AccumulatorPathVisitorTest} pairs
     * {@code assertEquals(x, x)} with {@code assertEquals(x.hashCode(),
     * x.hashCode())} eight times, never {@code @SuppressWarnings}-marked -
     * a routine equals/hashCode contract check, not a mistake.
     */
    @Test
    void selfComparisonPairedWithAHashCodeReflexivityCheckIsNotFlagged() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ByteOrderMarkTest {",
            "    @Test",
            "    void test0ArgConstructor() {",
            "        ByteOrderMark bom = ByteOrderMark.UTF_16BE;",
            "        assertEquals(bom, bom);",
            "        assertEquals(bom.hashCode(), bom.hashCode());",
            "    }",
            "}",
            ""));

        assertTrue(scan().findings().isEmpty(), scan().findings().toString());
    }

    /** An isolated self-comparison with no accompanying hashCode check must still fire - the pairing is what makes it legitimate. */
    @Test
    void selfComparisonWithoutAHashCodePairStillFires() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ByteOrderMarkTest {",
            "    @Test",
            "    void testSomethingElse() {",
            "        ByteOrderMark bom = ByteOrderMark.UTF_16BE;",
            "        assertEquals(bom, bom);",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("TAUTOLOGICAL_ORACLE", result.findings().get(0).rule());
    }

    /** A hashCode pair for a *different* base expression must not launder an unrelated self-comparison. */
    @Test
    void aHashCodePairOnADifferentExpressionDoesNotSilenceAnUnrelatedSelfComparison() throws IOException {
        Path dir = testDir();
        writeByteOrderMark(dir);
        Files.writeString(dir.resolve("ByteOrderMarkTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class ByteOrderMarkTest {",
            "    @Test",
            "    void testTwoInstances() {",
            "        ByteOrderMark a = ByteOrderMark.UTF_16BE;",
            "        ByteOrderMark b = ByteOrderMark.UTF_16BE;",
            "        assertEquals(a, a);",
            "        assertEquals(b.hashCode(), b.hashCode());",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("TAUTOLOGICAL_ORACLE", result.findings().get(0).rule());
    }

    /** The suppression is scoped to self-comparison (pattern 3) only - a constant-vs-constant tautology still fires. */
    @Test
    void theSuppressionDoesNotSilenceOtherTautologyPatterns() throws IOException {
        Files.writeString(testDir().resolve("MathTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import static org.junit.jupiter.api.Assertions.assertEquals;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class MathTest {",
            "    @SuppressWarnings(\"EqualsWithItself\")",
            "    @Test",
            "    void testAdd() {",
            "        assertEquals(4, 2 + 2);",
            "    }",
            "}",
            ""));

        OracleScanResult result = scan();

        assertEquals(1, result.findings().size(), result.findings().toString());
        assertEquals("TAUTOLOGICAL_ORACLE", result.findings().get(0).rule());
        assertTrue(result.findings().get(0).message().contains("compile-time constants"), result.findings().get(0).message());
    }
}
