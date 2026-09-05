package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The pure-logic half of {@link SubprocessWorkspace} - temp directories,
 * argument-file writing, classpath resolution - had zero direct coverage
 * despite none of it needing a spawned process.
 *
 * <p>{@code destroyProcessTree} is covered too, against a real child JVM
 * started by the JDK's single-file source launcher: {@link Process} cannot
 * be mocked here (Mockito is deliberately kept off this module's test
 * classpath - see the fixture-harness copy in the pom), and the kill path is
 * the single largest block of the class.
 *
 * <p>Killing the immediate child alone says nothing about the
 * {@code isWindows()} branch - {@code destroyForcibly()} runs either way, so
 * that assertion holds on both sides of the {@code if}. The descendant is the
 * branch's only observable effect, which is what
 * {@code destroyProcessTreeAlsoKillsAGrandchildProcessOnWindows} asserts.
 *
 * <p>What stays uncovered is only what no portable test can reach: the POSIX
 * {@code setPosixFilePermissions} failure arm, the {@code SystemRoot}-absent
 * {@code taskkill} fallback (the environment cannot be mutated without a
 * library), and the per-file catch inside {@code deleteQuietly}.
 */
class SubprocessWorkspaceTest {

    @Test
    void createPrivateTempDirectoryCreatesAUniqueDirectoryPerModule() {
        Path dirA = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        Path dirB = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        try {
            assertTrue(Files.isDirectory(dirA));
            assertTrue(Files.isDirectory(dirB));
            assertNotEquals(dirA, dirB, "two calls must not collide");
        } finally {
            SubprocessWorkspace.deleteQuietly(dirA);
            SubprocessWorkspace.deleteQuietly(dirB);
        }
    }

    @Test
    void createPrivateTempDirectorySanitizesPathSeparatorsInAnUnsafeModuleId() {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app/../../evil");
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
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
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
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
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
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        Files.writeString(dir.resolve("nested.txt"), "content", StandardCharsets.UTF_8);

        SubprocessWorkspace.deleteQuietly(dir);

        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteQuietlyOnAMissingDirectoryDoesNotThrow() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "proof-java-does-not-exist-" + System.nanoTime());

        assertDoesNotThrow(() -> SubprocessWorkspace.deleteQuietly(missing));
    }

    @Test
    void createPrivateTempDirectoryKeepsAnAlreadySafeModuleIdVerbatim() {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app-core_1.0");
        try {
            // Every character here is already in sanitize()'s allowed set, so the
            // module id must survive untouched - only the random suffix is added.
            assertTrue(dir.getFileName().toString().startsWith("proof-test-app-core_1.0"),
                dir.getFileName().toString());
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void createPrivateTempDirectoryReplacesEveryUnsafeCharacterWithAnUnderscore() {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "my group:app x");
        try {
            // ':' is outright illegal in a Windows file name and a space is merely
            // awkward everywhere - sanitize() collapses both to '_'.
            assertTrue(dir.getFileName().toString().startsWith("proof-test-my_group_app_x"),
                dir.getFileName().toString());
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void writeLinesOnAMissingDirectoryThrowsUncheckedIOException() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "proof-java-does-not-exist-" + System.nanoTime());
        List<String> lines = List.of("a");

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
            () -> SubprocessWorkspace.writeLines(missing, "list.txt", lines));

        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void writeLinesWithNoEntriesWritesAnEmptyFile() throws IOException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        try {
            Path file = SubprocessWorkspace.writeLines(dir, "empty.txt", List.of());

            assertTrue(Files.exists(file));
            assertEquals("", Files.readString(file, StandardCharsets.UTF_8));
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void writeClasspathArgFileOnAMissingDirectoryThrowsUncheckedIOException() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "proof-java-does-not-exist-" + System.nanoTime());
        List<String> classpath = List.of("a.jar");

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
            () -> SubprocessWorkspace.writeClasspathArgFile(missing, "cp.args", classpath));

        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void writeClasspathArgFileQuotesASingleEntryWithoutASeparator() throws IOException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        try {
            Path argFile = SubprocessWorkspace.writeClasspathArgFile(dir, "cp.args", List.of("/opt/lib/only one.jar"));

            // Quoting is unconditional even with nothing to separate: a lone entry
            // can still carry a space, which is exactly what this one does.
            assertEquals("-cp \"/opt/lib/only one.jar\"\n", Files.readString(argFile, StandardCharsets.UTF_8));
        } finally {
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    @Test
    void destroyProcessTreeKillsALiveChildJvm() throws IOException, InterruptedException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        Process process = null;
        try {
            // The JDK's single-file source launcher gives a real, long-lived child
            // process without needing a main class on Surefire's classpath.
            Path source = Files.writeString(dir.resolve("Sleeper.java"), """
                public class Sleeper {
                    public static void main(String[] args) throws Exception {
                        Thread.sleep(600_000L);
                    }
                }
                """,
                StandardCharsets.UTF_8);
            process = new ProcessBuilder(SubprocessWorkspace.javaExecutable(), source.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            assertTrue(process.isAlive(), "the child JVM should still be running before it is killed");

            SubprocessWorkspace.destroyProcessTree(process);

            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the child JVM should have been killed");
            assertFalse(process.isAlive());
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    /**
     * The reason {@code destroyProcessTree} exists at all: on Windows a
     * grandchild is not tied to the parent's lifetime, so
     * {@code destroyForcibly()} alone leaves it orphaned and only
     * {@code taskkill /F /T} reaches it. That makes the descendant - not the
     * immediate child, which dies either way - the one observable effect of
     * the {@code isWindows()} branch.
     *
     * <p>Windows-only by necessity rather than convenience: on POSIX the
     * production code deliberately does not kill the tree, so the same
     * assertion would be asserting a behavior that is not promised there.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void destroyProcessTreeAlsoKillsAGrandchildProcessOnWindows() throws IOException, InterruptedException {
        Path dir = SubprocessWorkspace.createPrivateTempDirectory("proof-test-", "app");
        Process parent = null;
        ProcessHandle grandchild = null;
        try {
            // Run with three arguments it spawns a copy of itself and records that
            // copy's pid; run with none (the copy) it just sleeps.
            Path source = Files.writeString(dir.resolve("Spawner.java"), """
                import java.nio.file.*;
                public class Spawner {
                    public static void main(String[] args) throws Exception {
                        if (args.length == 3) {
                            Process child = new ProcessBuilder(args[0], args[1])
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                            Files.writeString(Path.of(args[2]), Long.toString(child.pid()));
                        }
                        Thread.sleep(600_000L);
                    }
                }
                """,
                StandardCharsets.UTF_8);
            Path pidFile = dir.resolve("grandchild.pid");
            String java = SubprocessWorkspace.javaExecutable();
            parent = new ProcessBuilder(java, source.toString(), java, source.toString(), pidFile.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

            grandchild = ProcessHandle.of(awaitGrandchildPid(pidFile))
                .orElseThrow(() -> new AssertionError("the grandchild exited before it could be observed"));
            assertTrue(grandchild.isAlive(), "the grandchild should be running before the tree is killed");

            SubprocessWorkspace.destroyProcessTree(parent);

            assertTrue(parent.waitFor(60, TimeUnit.SECONDS), "the child JVM should have been killed");
            assertTrue(awaitExit(grandchild), "the grandchild should have been killed along with the tree");
        } finally {
            if (grandchild != null) {
                grandchild.destroyForcibly();
            }
            if (parent != null) {
                parent.destroyForcibly();
            }
            SubprocessWorkspace.deleteQuietly(dir);
        }
    }

    /** The pid file appears only once the child JVM has compiled and spawned its own child. */
    private static long awaitGrandchildPid(Path pidFile) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 600; attempt++) {
            if (Files.exists(pidFile)) {
                String pid = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
                if (!pid.isEmpty()) {
                    return Long.parseLong(pid);
                }
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("the child JVM never reported a grandchild pid");
    }

    private static boolean awaitExit(ProcessHandle handle) throws InterruptedException {
        for (int attempt = 0; attempt < 600 && handle.isAlive(); attempt++) {
            Thread.sleep(100L);
        }
        return !handle.isAlive();
    }

    @Test
    void destroyProcessTreeOnAnAlreadyExitedProcessDoesNotThrow() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(SubprocessWorkspace.javaExecutable(), "-version")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        process.waitFor(60, TimeUnit.SECONDS);

        // Killing an already-dead pid makes taskkill exit non-zero on Windows;
        // destroyProcessTree is best-effort and must swallow that either way.
        assertDoesNotThrow(() -> SubprocessWorkspace.destroyProcessTree(process));
        assertFalse(process.isAlive());
    }
}
