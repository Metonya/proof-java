package dev.proofjava.analysis.pertest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import dev.proofjava.analysis.subprocess.EvidenceDiagnostics;
import dev.proofjava.analysis.subprocess.ProcessOutputTail;
import dev.proofjava.analysis.subprocess.ProgressMarker;
import dev.proofjava.analysis.subprocess.SubprocessWorkspace;

/**
 * Spawns {@link PerTestDriver} as a genuinely separate OS process and
 * enforces the timeout D-51's spike could not avoid: PIT's mutation phase
 * can run for minutes after coverage finishes (PIT_SPIKE_PLAN note 2), so
 * this never waits for the child to exit cleanly. It polls for {@link
 * ProofLineExporter}'s output file - written the moment {@code
 * recordCoverage()} fires, before any mutation work starts - and forcibly
 * destroys the process the moment that file appears or the timeout elapses,
 * whichever comes first. A process still running at timeout with no output
 * file is a real collection failure (bytecode PIT's ASM cannot read, a
 * classpath problem, or a genuinely stuck run) surfaced by {@link
 * PerTestCollector} as {@code PER_TEST_COLLECTION_FAILED} - never silent
 * (hard rule 3a).
 *
 * <p>D-64: this used to discard the child's stdout AND stderr outright,
 * which made a real WTA dogfood result - two modules reporting {@code
 * entries: []} with no warning and no error - impossible to diagnose at
 * all. It now drains the merged stream like {@code MutationRunner} does,
 * keeps a failure tail, and can tee the whole thing to a
 * {@code --diagnostics-dir} log.
 */
public final class PerTestRunner {

    /**
     * Generous relative to D-52's measured scale (dropwizard: 2-3s;
     * assertj's 215-class slice: 20-30s) - a diff-scoped target is smaller
     * than either, but a cold JVM start and a large dependency classpath
     * both add fixed overhead this budget must absorb. {@code
     * --per-test-timeout} lets a caller raise it further - a real VS Code
     * dogfood against gson's 80-class production surface (deliberately
     * unscoped by a diff-free "scan the whole module anyway" request) blew
     * past 120s even with this budget already being generous for the
     * diff-scoped case it was measured against.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private static final Duration HEARTBEAT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(200);

    private static final String TEMP_DIR_PREFIX = "proof-pertest-";

    private static final String PROGRESS_PREFIX = "per-test: module '";

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
     * @param timeout           wall-clock budget before the process is force-killed.
     * @param diagnostics       where progress and subprocess logs go.
     * @return evidence if the exporter wrote its file before the timeout;
     *         empty if the run produced no output file at all.
     * @throws PerTestCollectionException on process failure, timeout, or a malformed output file.
     */
    public static Optional<PerTestModuleEvidence> run(String moduleId, Path repoRoot,
                                                        List<String> classPathElements,
                                                        List<String> codePaths,
                                                        List<String> targetClasses,
                                                        Duration timeout,
                                                        EvidenceDiagnostics diagnostics) {
        Path workDir = createWorkDir(moduleId);
        try (Writer log = diagnostics.openLog(moduleId, "pertest")) {
            List<String> ownClasspathEntries = SubprocessWorkspace.ownRuntimeClasspathEntries();
            // The minion PIT spawns needs org.pitest.coverage.execute.CoverageMinion and
            // ProofLineExporter on ITS OWN -cp (verified empirically: EntryPoint builds
            // the minion's classpath from ReportOptions.getClassPathElements(), not from the
            // driver JVM's own classpath) - appending this JVM's own runtime classpath (the
            // shaded jar in production, D-55; the full Maven classpath under test/dev) covers
            // both proof-java's classes and PIT's.
            List<String> classPathWithSelf = new java.util.ArrayList<>(classPathElements);
            classPathWithSelf.addAll(ownClasspathEntries);
            Path classpathFile = writeLines(moduleId, workDir, "classpath.txt", classPathWithSelf);
            Path codePathsFile = writeLines(moduleId, workDir, "codepaths.txt", codePaths);
            Path targetClassesFile = writeLines(moduleId, workDir, "targetclasses.txt", targetClasses);
            Path outputFile = workDir.resolve(ProofLineExporter.OUTPUT_FILE_NAME);
            Path classpathArgFile = writeClasspathArgFile(moduleId, workDir, ownClasspathEntries);

            ProcessBuilder pb = new ProcessBuilder(
                SubprocessWorkspace.javaExecutable(), "@" + classpathArgFile,
                PerTestDriver.class.getName(),
                moduleId, workDir.toString(), classpathFile.toString(), codePathsFile.toString(),
                targetClassesFile.toString(), String.valueOf(diagnostics.verbose()));
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true); // one chronological stream, one drain thread (D-64)

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                throw new PerTestCollectionException("Could not start the per-test coverage subprocess for module '"
                    + moduleId + "'", e);
            }

            ProcessOutputTail output = ProcessOutputTail.of(process.getInputStream(), log, null);
            Thread outputThread = output.start("proof-pertest-output");
            try {
                waitForOutputOrTimeout(process, outputFile, moduleId, timeout, diagnostics);
            } finally {
                SubprocessWorkspace.destroyProcessTree(process);
                ProcessOutputTail.joinQuietly(outputThread);
            }

            return readEvidence(moduleId, outputFile, output, timeout, diagnostics);
        } catch (IOException e) {
            throw new PerTestCollectionException("Could not write the diagnostics log for module '" + moduleId + "'", e);
        } finally {
            SubprocessWorkspace.deleteQuietly(workDir);
        }
    }

    public static Optional<PerTestModuleEvidence> run(String moduleId, Path repoRoot,
                                                        List<String> classPathElements,
                                                        List<String> codePaths,
                                                        List<String> targetClasses,
                                                        Duration timeout) {
        return run(moduleId, repoRoot, classPathElements, codePaths, targetClasses, timeout, EvidenceDiagnostics.none());
    }

    public static Optional<PerTestModuleEvidence> run(String moduleId, Path repoRoot,
                                                        List<String> classPathElements,
                                                        List<String> codePaths,
                                                        List<String> targetClasses) {
        return run(moduleId, repoRoot, classPathElements, codePaths, targetClasses, DEFAULT_TIMEOUT, EvidenceDiagnostics.none());
    }

    /**
     * D-64: the no-output-file case used to return {@link Optional#empty()}
     * with no explanation at all, indistinguishable from a legitimate "PIT
     * found nothing to instrument". The tail now travels with it so {@link
     * PerTestCollector} can say which it was.
     */
    private static Optional<PerTestModuleEvidence> readEvidence(String moduleId, Path outputFile,
                                                                  ProcessOutputTail output,
                                                                  Duration timeout,
                                                                  EvidenceDiagnostics diagnostics) {
        if (!Files.exists(outputFile)) {
            diagnostics.progress(PROGRESS_PREFIX + moduleId + "' - FAILED, no coverage export produced");
            throw new PerTestCollectionException("Module '" + moduleId
                + "' produced no per-test coverage export before its " + timeout.toSeconds() + "s timeout"
                + output.tailMessage());
        }
        try (InputStream in = Files.newInputStream(outputFile)) {
            PerTestModuleEvidence evidence = PerTestJsonReader.read(in);
            diagnostics.progress(PROGRESS_PREFIX + moduleId + "' - done, " + evidence.entries().size()
                + " method entr(ies)");
            return Optional.of(evidence);
        } catch (IOException e) {
            throw new PerTestCollectionException("Module '" + moduleId
                + "' produced an unreadable per-test result file", e);
        }
    }

    /** Returns as soon as {@code outputFile} exists or the process exits; otherwise blocks up to {@code timeout}, reporting progress along the way. */
    private static void waitForOutputOrTimeout(Process process, Path outputFile, String moduleId,
                                                Duration timeout, EvidenceDiagnostics diagnostics) {
        long start = System.nanoTime();
        long deadline = start + timeout.toNanos();
        long nextHeartbeat = start + HEARTBEAT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(outputFile) || !process.isAlive()) {
                return;
            }
            try {
                if (process.waitFor(POLL.toMillis(), TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (System.nanoTime() >= nextHeartbeat) {
                diagnostics.progress(PROGRESS_PREFIX + moduleId + "' - collecting coverage, "
                    + ProgressMarker.formatElapsed(Duration.ofNanos(System.nanoTime() - start)) + " elapsed");
                nextHeartbeat = System.nanoTime() + HEARTBEAT.toNanos();
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
