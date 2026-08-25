package dev.coverdict.analysis.pertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerTestClasspathLoaderTest {

    @TempDir
    Path repoRoot;

    @Test
    void anUnreadableListFileWarnsAndReturnsNoClasspath() {
        PerTestClasspathLoader.Result result = PerTestClasspathLoader.load(repoRoot, "app", "missing.txt");

        assertTrue(result.classPathElements().isEmpty());
        assertTrue(result.codePaths().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }

    @Test
    void aListWithNoDirectoryEntryWarnsBecauseThereIsNoCodePath() throws IOException {
        Path jar = repoRoot.resolve("dep.jar");
        Files.writeString(jar, "not a real jar, just needs to exist");
        Path listFile = repoRoot.resolve("cp.txt");
        Files.writeString(listFile, "dep.jar\n");

        PerTestClasspathLoader.Result result = PerTestClasspathLoader.load(repoRoot, "app", "cp.txt");

        assertTrue(result.classPathElements().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_CLASSPATH_MISSING", result.warnings().get(0).code());
    }

    @Test
    void directoryEntriesBecomeCodePathsAndAlsoStayOnTheFullClasspath() throws IOException {
        Path classesDir = repoRoot.resolve("target/classes");
        Files.createDirectories(classesDir);
        Path jar = repoRoot.resolve("dep.jar");
        Files.writeString(jar, "placeholder");
        Path listFile = repoRoot.resolve("cp.txt");
        Files.writeString(listFile, "# a comment\ntarget/classes\ndep.jar\n\n");

        PerTestClasspathLoader.Result result = PerTestClasspathLoader.load(repoRoot, "app", "cp.txt");

        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertEquals(2, result.classPathElements().size());
        assertEquals(1, result.codePaths().size());
        assertTrue(result.codePaths().get(0).endsWith("target" + java.io.File.separator + "classes"));
    }

    @Test
    void duplicateEntriesAreUsedOnce() throws IOException {
        Path classesDir = repoRoot.resolve("target/classes");
        Files.createDirectories(classesDir);
        Path listFile = repoRoot.resolve("cp.txt");
        Files.writeString(listFile, "target/classes\ntarget/classes\n");

        PerTestClasspathLoader.Result result = PerTestClasspathLoader.load(repoRoot, "app", "cp.txt");

        assertEquals(1, result.classPathElements().size());
    }
}
