package dev.proofjava.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.proofjava.analysis.model.ResolvedSourceFile;

class ExclusionFilterTest {

    private static ResolvedSourceFile file(String path) {
        return new ResolvedSourceFile("m", path, List.of(), 0, 0, true);
    }

    @Test
    void noGlobsKeepsEverything() {
        List<ResolvedSourceFile> files = List.of(file("a/B.java"), file("c/D.java"));
        assertEquals(files, ExclusionFilter.apply(files, List.of()));
    }

    @Test
    void doubleStarSlashMatchesAnyDepthIncludingZero() {
        // This exact pattern is coverdict's own pom.xml sonar.coverage.exclusions.
        List<ResolvedSourceFile> files = List.of(
            file("coverdict-cli/src/main/java/dev/proofjava/cli/Main.java"),
            file("Main.java"),
            file("coverdict-cli/src/main/java/dev/proofjava/cli/AnalyzeCommand.java")
        );
        List<ResolvedSourceFile> kept = ExclusionFilter.apply(files, List.of("**/Main.java"));
        assertEquals(1, kept.size());
        assertEquals("coverdict-cli/src/main/java/dev/proofjava/cli/AnalyzeCommand.java", kept.get(0).repoRelativePath());
    }

    @Test
    void singleStarStaysWithinOnePathSegment() {
        List<ResolvedSourceFile> files = List.of(file("README.md"), file("docs/README.md"));
        List<ResolvedSourceFile> kept = ExclusionFilter.apply(files, List.of("*.md"));
        assertEquals(1, kept.size());
        assertEquals("docs/README.md", kept.get(0).repoRelativePath(), "single * must not cross a directory boundary");
    }

    @Test
    void doubleStarInTheMiddleMatchesZeroOrMoreWholeDirectories() {
        List<ResolvedSourceFile> files = List.of(
            file("generated/Foo.java"),
            file("src/main/generated/Foo.java"),
            file("src/main/java/Foo.java")
        );
        List<ResolvedSourceFile> kept = ExclusionFilter.apply(files, List.of("**/generated/**"));
        assertEquals(1, kept.size());
        assertEquals("src/main/java/Foo.java", kept.get(0).repoRelativePath());
    }

    @Test
    void dotIsALiteralNotAnyCharacter() {
        // "Main.java" must not accidentally match "MainXjava" if '.' were
        // treated as regex "any character".
        List<ResolvedSourceFile> files = List.of(file("MainXjava"), file("Main.java"));
        List<ResolvedSourceFile> kept = ExclusionFilter.apply(files, List.of("Main.java"));
        assertEquals(1, kept.size());
        assertEquals("MainXjava", kept.get(0).repoRelativePath());
    }

    @Test
    void multipleGlobsAreOred() {
        List<ResolvedSourceFile> files = List.of(file("a/One.java"), file("b/Two.java"), file("c/Three.java"));
        List<ResolvedSourceFile> kept = ExclusionFilter.apply(files, List.of("**/One.java", "**/Two.java"));
        assertEquals(1, kept.size());
        assertEquals("c/Three.java", kept.get(0).repoRelativePath());
    }

    @Test
    void questionMarkMatchesExactlyOneCharacter() {
        List<ResolvedSourceFile> files = List.of(file("V1.java"), file("V12.java"));
        List<ResolvedSourceFile> kept = ExclusionFilter.apply(files, List.of("V?.java"));
        assertEquals(1, kept.size());
        assertEquals("V12.java", kept.get(0).repoRelativePath());
    }

    @Test
    void resultsAreASingleFilterPassNotCumulative() {
        // hard rule 4: exclusions apply once to build one filtered dataset -
        // this is really an invariant of how callers must use apply(), but
        // at minimum applying the same glob list twice must be idempotent.
        List<ResolvedSourceFile> files = List.of(file("a/One.java"), file("b/Two.java"));
        List<ResolvedSourceFile> once = ExclusionFilter.apply(files, List.of("**/One.java"));
        List<ResolvedSourceFile> twice = ExclusionFilter.apply(once, List.of("**/One.java"));
        assertEquals(once, twice);
        assertTrue(once.stream().noneMatch(f -> f.repoRelativePath().endsWith("One.java")));
    }
}
