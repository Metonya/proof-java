package dev.coverdict.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * Two M0-specified behaviours that had no implementation and that no corpus
 * phase happened to exercise - gson was the only JUnit 4 repo and used neither
 * shape, and dropwizard's pinned commit has no JUnit 4 at all (D-38), which is
 * exactly why they survived four validation phases unnoticed.
 *
 * <ul>
 *   <li>{@code org.junit.rules.ExpectedException} as a recognized oracle
 *       (docs/rules/README.md's JUnit 4 bullet)</li>
 *   <li>{@code @Disabled}/{@code @Ignore} findings carrying a
 *       {@code disabled test} note</li>
 * </ul>
 */
class Junit4OracleGapsTest {

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

    // --- ExpectedException ---

    @Test
    void theExpectedExceptionRuleIsRecognizedAsAnOracle() throws IOException {
        Files.writeString(testDir().resolve("ThrowsTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.Rule;",
            "import org.junit.Test;",
            "import org.junit.rules.ExpectedException;",
            "",
            "public class ThrowsTest {",
            "    @Rule",
            "    public ExpectedException thrown = ExpectedException.none();",
            "",
            "    @Test",
            "    public void rejectsBadInput() {",
            "        thrown.expect(IllegalArgumentException.class);",
            "        Integer.parseInt(\"nope\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertTrue(result.findings().isEmpty(),
            "thrown.expect(...) is the test's oracle: " + result.findings());
    }

    @Test
    void expectMessageAloneAlsoCounts() throws IOException {
        Files.writeString(testDir().resolve("MessageTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.Rule;",
            "import org.junit.Test;",
            "import org.junit.rules.ExpectedException;",
            "",
            "public class MessageTest {",
            "    @Rule",
            "    public ExpectedException thrown = ExpectedException.none();",
            "",
            "    @Test",
            "    public void statesTheMessage() {",
            "        thrown.expectMessage(\"boom\");",
            "        throw new IllegalStateException(\"boom\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertTrue(result.findings().isEmpty(), result.findings().toString());
    }

    /**
     * The field-type anchoring is deliberately narrow (D-43): a same-named
     * variable whose declared type is something else entirely must not be
     * mistaken for the JUnit rule.
     */
    @Test
    void aSameNamedFieldOfAnUnrelatedTypeIsNotTreatedAsTheRule() throws IOException {
        Files.writeString(testDir().resolve("DecoyTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.Test;",
            "",
            "public class DecoyTest {",
            "    public StringBuilder thrown = new StringBuilder();",
            "",
            "    @Test",
            "    public void looksSimilarButIsNotAnOracle() {",
            "        thrown.append(\"expect\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size(), "a StringBuilder named 'thrown' is not an oracle");
        assertEquals("NO_RECOGNIZED_ORACLE", result.findings().get(0).rule());
    }

    // --- @Disabled / @Ignore note ---

    @Test
    void aDisabledTestIsStillAnalyzedAndItsFindingSaysSo() throws IOException {
        Files.writeString(testDir().resolve("DisabledTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Disabled;",
            "import org.junit.jupiter.api.Test;",
            "",
            "class DisabledTest {",
            "    @Test",
            "    @Disabled(\"flaky\")",
            "    void noOracleHere() {",
            "        System.out.println(\"nothing verified\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size(), "a disabled test is analyzed, not skipped");
        assertTrue(result.findings().get(0).message().contains("(disabled test)"),
            result.findings().get(0).message());
    }

    @Test
    void theJunit4IgnoreAnnotationCarriesTheSameNote() throws IOException {
        Files.writeString(testDir().resolve("IgnoredTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.Ignore;",
            "import org.junit.Test;",
            "",
            "public class IgnoredTest {",
            "    @Test",
            "    @Ignore",
            "    public void noOracleHere() {",
            "        System.out.println(\"nothing verified\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size());
        assertTrue(result.findings().get(0).message().contains("(disabled test)"),
            result.findings().get(0).message());
    }

    @Test
    void aClassLevelDisabledAnnotationCountsForItsMethods() throws IOException {
        Files.writeString(testDir().resolve("WholeClassDisabledTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Disabled;",
            "import org.junit.jupiter.api.Test;",
            "",
            "@Disabled(\"whole suite parked\")",
            "class WholeClassDisabledTest {",
            "    @Test",
            "    void noOracleHere() {",
            "        System.out.println(\"nothing verified\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size());
        assertTrue(result.findings().get(0).message().contains("(disabled test)"),
            result.findings().get(0).message());
    }

    @Test
    void anEnabledTestGetsNoSuchNote() throws IOException {
        Files.writeString(testDir().resolve("PlainTest.java"), String.join("\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class PlainTest {",
            "    @Test",
            "    void noOracleHere() {",
            "        System.out.println(\"nothing verified\");",
            "    }",
            "}",
            ""));

        OracleScanResult result = OracleRuleEngine.scan(repoRoot, List.of(module()), 17, "UTF-8", null);

        assertEquals(1, result.findings().size());
        Finding finding = result.findings().get(0);
        assertFalse(finding.message().contains("disabled"), finding.message());
    }
}
