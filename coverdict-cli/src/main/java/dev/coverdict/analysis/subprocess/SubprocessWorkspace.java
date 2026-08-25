package dev.coverdict.analysis.subprocess;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Shared plumbing for coverdict's PIT-driving subprocesses (L2 per-test
 * coverage, L3 mutation): a private temp work directory, argument-file
 * writing (sidesteps classpath-string length and OS argv quirks), and the
 * current JVM's own runtime classpath for the child's {@code -cp}. Each
 * caller wraps the unchecked failures below into its own package's
 * collection-failure exception - this class stays exception-vocabulary-free
 * so it can be shared across packages.
 */
public final class SubprocessWorkspace {

    private SubprocessWorkspace() {
    }

    /**
     * SonarQube java:S5443: the OS temp directory is shared/publicly
     * writable, so the created directory - which briefly holds the child
     * process's argument files and result output - is restricted to the
     * current user right after creation, on platforms where that is
     * actually achievable. {@code Files.createTempDirectory} has no
     * portable permissions overload; on a POSIX file system, {@code
     * setPosixFilePermissions} reliably restricts to owner-only. On Windows
     * there is no equivalent guarantee through the JDK - empirically,
     * {@code File.setReadable}/{@code setWritable(false, false)} ("deny
     * everyone else") both return {@code false} (unsupported) rather than
     * applying an ACL - so this falls back to the OS temp directory's own
     * default per-user ACL (in practice already user-scoped under {@code
     * %LOCALAPPDATA%\Temp}) instead of failing the run over an API that
     * cannot succeed there.
     *
     * @throws UncheckedIOException if the directory cannot be created or restricted.
     */
    public static Path createPrivateTempDirectory(String prefix, String moduleId) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory(prefix + sanitize(moduleId));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a temp directory for module '" + moduleId + "'", e);
        }
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            try {
                Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwx------"));
            } catch (IOException e) {
                throw new UncheckedIOException(
                    "Could not restrict the temp directory for module '" + moduleId + "' to the current user", e);
            }
        }
        return workDir;
    }

    private static String sanitize(String moduleId) {
        return moduleId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** @throws UncheckedIOException if the file cannot be written. */
    public static Path writeLines(Path dir, String name, List<String> lines) {
        try {
            return Files.write(dir.resolve(name), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A Java {@code @argfile} containing a single {@code -cp "<classpath>"}
     * token, for launching a child JVM whose classpath is too long to pass
     * safely as a literal {@code -cp} argument (Windows' ~8191-char command
     * line limit - the same reason {@link #writeLines} exists for PIT's own
     * {@code ReportOptions} inputs).
     *
     * <p>Backslashes are normalized to {@code /} before quoting - found
     * empirically running {@code MutationRunnerIT} on Windows: a quoted
     * {@code @argfile} token containing a real {@code C:\Users\...} path
     * silently mis-tokenizes (backslash is special inside a quoted token)
     * and the child fails with {@code ClassNotFoundException} for its own
     * main class, not even PIT's. A bare, unquoted token round-trips
     * backslashes correctly, but quoting is unconditionally needed here
     * since any single classpath entry can carry a space. The JVM accepts
     * {@code /} in classpath entries on Windows exactly like {@code \}, so
     * this sidesteps the tokenizer bug rather than working around it.
     *
     * @throws UncheckedIOException if the file cannot be written.
     */
    public static Path writeClasspathArgFile(Path dir, String name, List<String> classpathEntries) {
        String joined = String.join(File.pathSeparator, classpathEntries).replace('\\', '/');
        try {
            return Files.writeString(dir.resolve(name), "-cp \"" + joined + "\"\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * The current JVM's own runtime classpath, split into entries. In
     * production ({@code java -jar coverdict.jar ...}) this is the single
     * shaded jar, which already contains PIT (D-55). Under Maven/Surefire
     * (tests, {@code -Pmutation-it}) it is instead the full multi-jar
     * compile+test classpath - correctly including PIT's separate
     * dependency jars there too, unlike the single-class self-location this
     * replaced, which resolved only to a compiled-output directory with no
     * PIT classes on it (found running {@code MutationRunnerIT}:
     * {@code NoClassDefFoundError: org.pitest.mutationtest.config.ReportOptions}).
     * The child driver process needs this for its own {@code -cp} (to load
     * PIT's {@code EntryPoint} and coverdict's own driver/listener classes);
     * PIT's mutation minion needs it appended to {@code ReportOptions}'
     * classpath for the same reason, one entry at a time (never as one
     * {@code File.pathSeparator}-joined blob - PIT reads each collection
     * element as a single path).
     */
    public static List<String> ownRuntimeClasspathEntries() {
        return List.of(System.getProperty("java.class.path").split(File.pathSeparator));
    }

    /**
     * Kills {@code process} and, on Windows, its entire descendant tree via
     * {@code taskkill /F /T}. {@code Process.destroyForcibly()} alone only
     * reaches the immediate child - PIT spawns its own minion JVM as a
     * grandchild, which Windows never ties to the parent's lifetime (no
     * process group by default). Found running {@code MutationRunnerIT}: a
     * handful of budget-exceeded runs left 80+ orphaned minion JVMs
     * consuming multiple GB of memory, each spawned faster than repeated
     * {@code destroyForcibly()} calls on the immediate child alone could
     * ever catch up with. Best-effort: a {@code taskkill} failure still
     * falls through to {@code destroyForcibly()} on the immediate child, so
     * this is never worse than the previous behavior, only ever better.
     */
    public static void destroyProcessTree(Process process) {
        if (isWindows()) {
            try {
                new ProcessBuilder(taskkillExecutable(), "/F", "/T", "/PID", String.valueOf(process.pid()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
            } catch (IOException ignored) {
                // best-effort - falls through to destroyForcibly() below regardless
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        process.destroyForcibly();
    }

    /**
     * SonarQube java:S4036: a bare {@code "taskkill"} argument resolves
     * through the process's {@code PATH}, which could be hijacked by an
     * earlier, attacker-writable directory. {@code %SystemRoot%} (always
     * {@code C:\Windows}, set by the OS, never user-writable) pins the
     * real System32 binary directly, with the bare command name as a
     * last-resort fallback only if that environment variable is somehow
     * absent.
     */
    private static String taskkillExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            return "taskkill";
        }
        return Path.of(systemRoot, "System32", "taskkill.exe").toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static void deleteQuietly(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp directory - never worth failing the run over
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory - never worth failing the run over
        }
    }
}
