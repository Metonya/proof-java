package dev.coverdict.analysis.pertest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spawns {@link PerTestDriver} as a genuinely separate OS process and
 * enforces the timeout D-51's spike could not avoid: PIT's mutation phase
 * can run for minutes after coverage finishes (PIT_SPIKE_PLAN note 2), so
 * this never waits for the child to exit cleanly. It polls for {@link
 * CoverdictLineExporter}'s output file - written the moment {@code
 * recordCoverage()} fires, before any mutation work starts - and forcibly
 * destroys the process the moment that file appears or the timeout elapses,
 * whichever comes first. A process still running at timeout with no output
 * file is a real collection failure (bytecode PIT's ASM cannot read, a
 * classpath problem, or a genuinely stuck run) surfaced by {@link
 * PerTestCollector} as {@code PER_TEST_COLLECTION_FAILED} - never silent
 * (hard rule 3a).
 */
public final class PerTestRunner {

    /**
     * Generous relative to D-52's measured scale (dropwizard: 2-3s;
     * assertj's 215-class slice: 20-30s) - a diff-scoped target is smaller
     * than either, but a cold JVM start and a large dependency classpath
     * both add fixed overhead this budget must absorb.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private PerTestRunner() {
    }

    /**
     * @param classPathElements the full PIT-ready classpath (module output
     *                          directories first, then dependency jars).
     * @param codePaths         the subset of {@code classPathElements} that
     *                          are the module's own compiled-output
     *                          directories (never a dependency jar).
     * @param targetClasses     FQCN globs for the changed production classes
     *                          this run should collect evidence for.
     * @return evidence if the exporter wrote its file before the timeout;
     *         empty if the run found no mutable target (PIT's own coverage
     *         skip, not a failure) - the two are distinguished by exit code.
     * @throws PerTestCollectionException on process failure, timeout, or a malformed output file.
     */
    public static java.util.Optional<PerTestModuleEvidence> run(String moduleId, Path repoRoot,
                                                                  List<String> classPathElements,
                                                                  List<String> codePaths,
                                                                  List<String> targetClasses) {
        Path workDir = createPrivateTempDirectory(moduleId);
        try {
            String ownClasspath = ownClasspath();
            // The minion PIT spawns needs org.pitest.coverage.execute.CoverageMinion and
            // CoverdictLineExporter on ITS OWN -cp (verified empirically: EntryPoint builds
            // the minion's classpath from ReportOptions.getClassPathElements(), not from the
            // driver JVM's own classpath) - coverdict.jar already carries all of PIT, shaded
            // in (D-55), so appending coverdict's own jar/classes location is sufficient.
            List<String> classPathWithSelf = new java.util.ArrayList<>(classPathElements);
            classPathWithSelf.add(ownClasspath);
            Path classpathFile = writeLines(workDir, "classpath.txt", classPathWithSelf);
            Path codePathsFile = writeLines(workDir, "codepaths.txt", codePaths);
            Path targetClassesFile = writeLines(workDir, "targetclasses.txt", targetClasses);
            Path outputFile = workDir.resolve(CoverdictLineExporter.OUTPUT_FILE_NAME);

            ProcessBuilder pb = new ProcessBuilder(
                javaExecutable(), "-cp", ownClasspath,
                PerTestDriver.class.getName(),
                moduleId, workDir.toString(), classpathFile.toString(), codePathsFile.toString(),
                targetClassesFile.toString());
            pb.directory(repoRoot.toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                throw new PerTestCollectionException("Could not start the per-test coverage subprocess for module '"
                    + moduleId + "'", e);
            }
            try {
                waitForOutputOrTimeout(process, outputFile);
            } finally {
                process.destroyForcibly();
            }

            if (!Files.exists(outputFile)) {
                return java.util.Optional.empty(); // no mutable target found - PIT's own skip, not a failure
            }
            try (InputStream in = Files.newInputStream(outputFile)) {
                return java.util.Optional.of(PerTestJsonReader.read(in));
            } catch (IOException e) {
                throw new PerTestCollectionException("Module '" + moduleId + "' produced an unreadable per-test result file", e);
            }
        } finally {
            deleteQuietly(workDir);
        }
    }

    /** Returns as soon as {@code outputFile} exists or the process exits; otherwise blocks up to {@link #TIMEOUT}. */
    private static void waitForOutputOrTimeout(Process process, Path outputFile) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(outputFile) || !process.isAlive()) {
                return;
            }
            try {
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static Path writeLines(Path dir, String name, List<String> lines) {
        try {
            return Files.write(dir.resolve(name), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /** coverdict's own running jar (or classes directory in tests) - already contains PIT, shaded in (D-55). */
    private static String ownClasspath() {
        try {
            return Path.of(PerTestRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not resolve coverdict's own classpath for the per-test subprocess", e);
        }
    }

    /**
     * SonarQube java:S5443: the OS temp directory is shared/publicly
     * writable, so the created directory - which briefly holds this
     * module's classpath list and PIT's coverage output - is restricted to
     * the current user right after creation, on platforms where that is
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
     */
    private static Path createPrivateTempDirectory(String moduleId) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("coverdict-pertest-" + sanitize(moduleId));
        } catch (IOException e) {
            throw new PerTestCollectionException("Could not create a temp directory for module '" + moduleId + "'", e);
        }
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            try {
                Files.setPosixFilePermissions(workDir, PosixFilePermissions.fromString("rwx------"));
            } catch (IOException e) {
                throw new PerTestCollectionException(
                    "Could not restrict the temp directory for module '" + moduleId + "' to the current user", e);
            }
        }
        return workDir;
    }

    private static String sanitize(String moduleId) {
        return moduleId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
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
