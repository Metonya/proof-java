package dev.coverdict.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The pure-logic half of {@link SubprocessWorkspace} - temp directories,
 * argument-file writing, classpath resolution - had zero direct coverage
 * despite none of it needing a spawned process. {@code destroyProcessTree}
 * is not covered here: it needs a real {@link Process}, the same reason
 * {@code PerTestRunner}/{@code MutationRunner} stay untested outside
 * {@code -Pmutation-it}.
 */
class SubprocessWorkspaceTest {

    @Test
    void createPrivateTempDirectoryCreatesAUniqueDirectoryPerModule() throws IOException {
        Path dirA = SubprocessWorkspace.createPrivateTempDirectory("coverdict-test-", "app");
        Path dirB = SubprocessWorkspace.createPrivateTempDirectory("coverdict-test-", "app");
        try {
            assertTrue(Files.isDirectory(dirA));
            assertTrue(Files.isDirectory(dirB));
            assertFalse(dirA.equals(dirB), "two calls must not collide");
        } finally {
            SubprocessWorkspace.deleteQuietly(dirA);
            SubprocessWorkspace.deleteQuietly(dirB);
        }
    }

    @Test
    void createPrivateTempDirectorySanitizesPathSeparatorsInAnUnsafeModuleId() throws IOException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("coverdict-test-", "app/../../evil");
        try {
            assertTrue(Files.isDirectory(dir));
            // A slash-containing module id must not escape as a path traversal -
            // sanitize() replaces '/' (and '\') with '_', so the single resulting
            // directory name carries no separator at all.
            assertFalse(dir.getFileName().toString().contains("/"));
            assertFalse(dir.getFileName().toString().contains("\\"));
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void writeLinesWritesOneEntryPerLine() throws IOException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("coverdict-test-", "app");
        try {
            Path file = SubprocessWorkspace.writeLines(dir, "list.txt", List.of("a", "b", "c"));

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(List.of("a", "b", "c"), lines);
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void writeClasspathArgFileQuotesAndNormalizesBackslashes() throws IOException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("coverdict-test-", "app");
        try {
            Path argFile = SubprocessWorkspace.writeClasspathArgFile(dir, "cp.args",
                List.of("C:\\Users\\dev\\a.jar", "C:\\Users\\dev\\b.jar"));

            String content = Files.readString(argFile, StandardCharsets.UTF_8);
            assertEquals("-cp \"C:/Users/dev/a.jar" + File.pathSeparator + "C:/Users/dev/b.jar\"\n", content);
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void javaExecutableIsUnderTheRunningJdksHome() {
        String java = SubprocessWorkspace.javaExecutable();

        assertTrue(java.startsWith(System.getProperty("java.home")), java);
        assertTrue(java.contains("bin"), java);
    }

    @Test
    void ownRuntimeClasspathEntriesMatchesTheCurrentJvmsClasspath() {
        List<String> entries = SubprocessWorkspace.ownRuntimeClasspathEntries();

        assertEquals(List.of(System.getProperty("java.class.path").split(File.pathSeparator)), entries);
        assertFalse(entries.isEmpty());
    }

    @Test
    void deleteQuietlyRemovesADirectoryTreeWithoutThrowing() throws IOException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("coverdict-test-", "app");
        Files.writeString(dir.resolve("nested.txt"), "content", StandardCharsets.UTF_8);

        SubprocessWorkspace.deleteQuietly(dir);

        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteQuietlyOnAMissingDirectoryDoesNotThrow() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "coverdict-does-not-exist-" + System.nanoTime());

        SubprocessWorkspace.deleteQuietly(missing);
    }
}
