package dev.coverdict.analysis.subprocess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;

/**
 * Shared plumbing for coverdict's PIT-driving subprocesses (L2 per-test
 * coverage, L3 mutation): a private temp work directory, argument-file
 * writing (sidesteps classpath-string length and OS argv quirks), and
 * self-locating coverdict's own running jar for the child's {@code -cp}.
 * Each caller wraps the unchecked failures below into its own package's
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

    public static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * coverdict's own running jar (or classes directory in tests) - already
     * contains PIT, shaded in (D-55). The minion PIT spawns needs this on
     * ITS OWN {@code -cp} (verified empirically: {@code EntryPoint} builds
     * the minion's classpath from {@code ReportOptions.getClassPathElements()},
     * not from the driver JVM's own classpath).
     *
     * @throws IllegalStateException if coverdict's own code source location cannot be resolved.
     */
    public static String ownClasspath(Class<?> anchor) {
        try {
            return Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not resolve coverdict's own classpath for the subprocess", e);
        }
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
