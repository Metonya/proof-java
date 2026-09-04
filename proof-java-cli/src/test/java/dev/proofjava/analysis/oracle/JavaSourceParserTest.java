package dev.proofjava.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.javaparser.ast.CompilationUnit;

/**
 * {@code JavaSourceParser}'s {@code mapLanguageLevel} switch has one
 * hand-written case per documented level (8-16, 18-21; 17 is the {@code
 * default}). Before this, every test in the suite passed either the CLI's
 * default (17, which never even touches the switch - it falls through to
 * {@code default}) or, in corpus harness runs, 11 - not a unit test at all.
 * The other eleven cases had literally never executed: a copy-paste typo
 * mapping {@code case 12} to {@code JAVA_13} would have shipped silently.
 */
class JavaSourceParserTest {

    @TempDir
    Path repoRoot;

    private Optional<CompilationUnit> parseAtLevel(int languageLevel, String source) throws IOException {
        Path file = repoRoot.resolve("Probe" + languageLevel + ".java");
        Files.writeString(file, source);
        JavaSourceParser parser = new JavaSourceParser(repoRoot, List.of(), languageLevel, "UTF-8");
        return parser.parse(file);
    }

    /**
     * Every documented level dispatches to *some* real language level and
     * parses ordinary syntax without throwing - exercises every switch branch
     * at least once, closing the "never executed" gap even though it does not
     * by itself prove each number maps to the *correct* enum constant (the two
     * tests below do that with level-specific syntax).
     */
    @ParameterizedTest
    @ValueSource(ints = {8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21})
    void everyDocumentedLanguageLevelParsesOrdinarySyntax(int level) throws IOException {
        Optional<CompilationUnit> cu = parseAtLevel(level, "class Probe { int x = 1; }");

        assertTrue(cu.isPresent(), "level " + level + " failed to parse plain Java");
    }

    /**
     * Record classes (JEP 395) became final/non-preview syntax at language
     * level 16 - a second, independent semantic probe covering the switch's
     * upper half (case 16 specifically).
     */
    @Test
    void recordDeclarationsAreRejectedBelowJava16ButAcceptedFromJava16Onward() throws IOException {
        String source = "record Probe(int x) { }";

        Optional<CompilationUnit> atJava14 = parseAtLevel(14, source);
        Optional<CompilationUnit> atJava16 = parseAtLevel(16, source);

        assertTrue(atJava14.isEmpty(), "record declarations were not final syntax before Java 16 - level 14 must reject it");
        assertTrue(atJava16.isPresent(), "record declarations are valid from Java 16 onward");
    }
}
