package dev.coverdict.analysis.pertest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.coverdict.analysis.subprocess.SubprocessWorkspace;

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

    private static final String TEMP_DIR_PREFIX = "coverdict-pertest-";

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
        Path workDir = createWorkDir(moduleId);
        try {
            List<String> ownClasspathEntries = SubprocessWorkspace.ownRuntimeClasspathEntries();
            // The minion PIT spawns needs org.pitest.coverage.execute.CoverageMinion and
            // CoverdictLineExporter on ITS OWN -cp (verified empirically: EntryPoint builds
            // the minion's classpath from ReportOptions.getClassPathElements(), not from the
            // driver JVM's own classpath) - appending this JVM's own runtime classpath (the
            // shaded jar in production, D-55; the full Maven classpath under test/dev) covers
            // both coverdict's classes and PIT's.
            List<String> classPathWithSelf = new java.util.ArrayList<>(classPathElements);
            classPathWithSelf.addAll(ownClasspathEntries);
            Path classpathFile = writeLines(moduleId, workDir, "classpath.txt", classPathWithSelf);
            Path codePathsFile = writeLines(moduleId, workDir, "codepaths.txt", codePaths);
            Path targetClassesFile = writeLines(moduleId, workDir, "targetclasses.txt", targetClasses);
            Path outputFile = workDir.resolve(CoverdictLineExporter.OUTPUT_FILE_NAME);
            Path classpathArgFile = writeClasspathArgFile(moduleId, workDir, ownClasspathEntries);

            ProcessBuilder pb = new ProcessBuilder(
                SubprocessWorkspace.javaExecutable(), "@" + classpathArgFile,
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
                SubprocessWorkspace.destroyProcessTree(process);
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
            SubprocessWorkspace.deleteQuietly(workDir);
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

    private static Path createWorkDir(String moduleId) {
        try {
            return SubprocessWorkspace.createPrivateTempDirectory(TEMP_DIR_PREFIX, moduleId);
        } catch (UncheckedIOException e) {
            throw new PerTestCollectionException("Could not create a temp directory for module '" + moduleId + "'", e);
        }
    }

    private static Path writeLines(String moduleId, Path dir, String name, List<String> lines) {
        try {
            return SubprocessWorkspace.writeLines(dir, name, lines);
        } catch (UncheckedIOException e) {
            throw new PerTestCollectionException("Could not write '" + name + "' for module '" + moduleId + "'", e);
        }
    }

    private static Path writeClasspathArgFile(String moduleId, Path dir, List<String> classpathEntries) {
        try {
            return SubprocessWorkspace.writeClasspathArgFile(dir, "driver-cp.args", classpathEntries);
        } catch (UncheckedIOException e) {
            throw new PerTestCollectionException("Could not write the driver classpath arg file for module '" + moduleId + "'", e);
        }
    }
}
