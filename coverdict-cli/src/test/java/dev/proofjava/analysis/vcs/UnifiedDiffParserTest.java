package dev.proofjava.analysis.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every fixture here is either captured verbatim from a real {@code git
 * diff --unified=0 --find-renames --no-color --src-prefix=a/ --dst-prefix=b/}
 * run (see {@code fixtures/diff/}), or - for the CRLF case - built to match
 * that exact byte shape with CRLF line endings substituted in. No fixture is
 * hand-guessed.
 */
class UnifiedDiffParserTest {

    private static final Path FIXTURES = Path.of("../fixtures/diff");

    @Test
    void accumulatesAllHunksOfAMultiHunkFile() throws IOException {
        Map<String, SortedSet<Integer>> result = parse("multi-hunk.diff");
        assertEquals(Set.of("coverdict-cli/src/test/java/dev/proofjava/analysis/jacoco/JacocoXmlParserTest.java"), result.keySet());
        SortedSet<Integer> lines = result.values().iterator().next();
        assertEquals(Set.of(86, 87, 88, 89, 90, 93, 94, 95, 96, 97, 98, 102, 103, 104, 105, 106, 107), lines);
    }

    @Test
    void newFileWithNoCommaOldSideCountsAllAddedLines() throws IOException {
        Map<String, SortedSet<Integer>> result = parse("new-file.diff");
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), result.get("src/main/java/com/example/New.java"));
    }

    /**
     * Four distinct causes, same observable contract: no new-side lines means
     * no {@code changedFiles} entry at all - a full deletion ({@code +++
     * /dev/null}), a hunk whose new-side count is {@code 0} (pure removal), a
     * pure rename with no content change (no {@code +++} line at all), and a
     * binary diff (same).
     */
    @ParameterizedTest
    @ValueSource(strings = {"deleted-file.diff", "pure-removal-hunk.diff", "pure-rename-no-changes.diff", "binary-file.diff"})
    void fixturesWithNoNewSideLinesContributeNoEntry(String fixtureName) throws IOException {
        Map<String, SortedSet<Integer>> result = parse(fixtureName);
        assertTrue(result.isEmpty(), result.toString());
    }

    @Test
    void unicodeAndSpacePathIsResolvedPastTheTrailingTab() throws IOException {
        Map<String, SortedSet<Integer>> result = parse("unicode-and-space-path.diff");
        assertEquals(Set.of(1, 2), result.get("src/main/java/com/exämple/Ünïcödé File.java"));
    }

    @Test
    void renameWithModificationAttributesTheNewLinesToTheNewPathOnly() throws IOException {
        // Distinct from pure-rename-no-changes.diff (100% similarity, no
        // hunk): a rename below 100% similarity carries both a "rename
        // from/to" pair and a real hunk, and new-code lines must land on the
        // new path, never the old one (M1c criterion 7).
        Map<String, SortedSet<Integer>> result = parse("rename-with-modification.diff");
        assertEquals(Set.of(6), result.get("src/main/java/com/example/Renamed.java"));
        assertFalse(result.containsKey("src/main/java/com/example/Old.java"));
        assertEquals(1, result.size());
    }

    @Test
    void noCommaSingleLineHunkIsOneLine() throws IOException {
        Map<String, SortedSet<Integer>> result = parse("no-comma-single-line-hunk.diff");
        assertEquals(Set.of(3), result.get("src/main/java/com/example/One.java"));
    }

    @Test
    void multiFileDiffKeepsEachFilesLinesSeparate() throws IOException {
        Map<String, SortedSet<Integer>> result = parse("multi-file-mixed.diff");
        assertEquals(Set.of(8, 9, 10, 11), result.get("src/main/java/com/example/Calc.java"));
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), result.get("src/main/java/com/example/New.java"));
        assertFalse(result.containsKey("src/main/java/com/example/Renamed.java"));
        assertFalse(result.containsKey("src/main/java/com/example/Old.java"));
        assertFalse(result.containsKey("src/main/java/com/example/blob.bin"));
        assertEquals(2, result.size());
    }

    @Test
    void crlfDiffTextIsParsedTheSameAsLf() throws IOException {
        // Built in-memory rather than as a checked-in fixture: .gitattributes
        // normalizes everything under fixtures/** to LF on commit (D-22), so
        // a checked-in CRLF file would lose the very line endings this test
        // exists to exercise.
        String lfText = Files.readString(FIXTURES.resolve("no-comma-single-line-hunk.diff"));
        String crlfText = lfText.replace("\n", "\r\n");

        Map<String, SortedSet<Integer>> result = UnifiedDiffParser.parse(crlfText);
        assertEquals(Set.of(3), result.get("src/main/java/com/example/One.java"));
    }

    @Test
    void addedCommentLineStartingWithPlusPlusIsNotMistakenForAFileBoundary() {
        // An added source line whose own text starts with "++ " (plausibly a
        // comment) is prefixed by git's own "+" marker, producing a diff
        // line that reads "+++ note: ..." - structurally identical to a
        // "+++ b/<path>" file-header line. It must never be read as one; the
        // "previous line started with '--- '" gate in UnifiedDiffParser
        // exists exactly to reject this (the previous line here is a "@@"
        // hunk header, not "--- ").
        String diff = String.join("\n",
            "diff --git a/src/main/java/com/example/Calc.java b/src/main/java/com/example/Calc.java",
            "index aaaaaaa..bbbbbbb 100644",
            "--- a/src/main/java/com/example/Calc.java",
            "+++ b/src/main/java/com/example/Calc.java",
            "@@ -5,0 +6,2 @@ public class Calc {",
            "+++ note: not a file header, just an added comment line",
            "+    int y = 2;",
            "");
        Map<String, SortedSet<Integer>> result = UnifiedDiffParser.parse(diff);
        assertEquals(Set.of(6, 7), result.get("src/main/java/com/example/Calc.java"));
    }

    private static Map<String, SortedSet<Integer>> parse(String fixtureName) throws IOException {
        String text = Files.readString(FIXTURES.resolve(fixtureName));
        return UnifiedDiffParser.parse(text);
    }
}
