package dev.proofjava.analysis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepoPathsTest {

    @Test
    void joinTrimsATrailingSlashOnTheBase() {
        assertEquals("src/main/A.java", RepoPaths.join("src/main/", "A.java"));
    }

    @Test
    void joinWithADotBaseReturnsTheSuffixUnchanged() {
        assertEquals("A.java", RepoPaths.join(".", "A.java"));
    }

    @Test
    void joinWithAnEmptyOrNullBaseReturnsTheSuffixUnchanged() {
        assertEquals("A.java", RepoPaths.join("", "A.java"));
        assertEquals("A.java", RepoPaths.join(null, "A.java"));
    }

    @Test
    void normalizeSeparatorsConvertsBackslashesToForwardSlashes() {
        assertEquals("src/main/A.java", RepoPaths.normalizeSeparators("src\\main\\A.java"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", "a/../../x", "/etc/passwd", "C:/x", "a/b/../../../x"})
    void isEscapingRepoRootDetectsAbsoluteAndRootEscapingPaths(String path) {
        assertTrue(RepoPaths.isEscapingRepoRoot(path), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/../b", "./a", "a/b/c", "a/./b", "a/b/../c"})
    void isEscapingRepoRootAcceptsPathsThatStayWithinTheRoot(String path) {
        assertFalse(RepoPaths.isEscapingRepoRoot(path), path);
    }
}
