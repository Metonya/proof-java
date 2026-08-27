package dev.coverdict.analysis.mutation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.coverdict.analysis.subprocess.EvidenceDiagnostics;
import dev.coverdict.analysis.subprocess.ProcessOutputTail;
import dev.coverdict.analysis.subprocess.ProgressMarker;
import dev.coverdict.analysis.subprocess.SubprocessWorkspace;

/**
 * Spawns {@link MutationDriver} as a genuinely separate OS process and lets
 * it run to completion, bounded by a budget - L3's sibling of {@link
 * dev.coverdict.analysis.pertest.PerTestRunner}, but never force-killed on
 * the happy path: unlike L2 (which only needs the coverage phase and force
 * -kills the moment it's done), the mutation phase itself is the evidence
 * this collects, so it must be allowed to actually finish.
 *
 * <p>The child's stdout and stderr are merged into one stream and drained on
 * a dedicated thread from the moment the process is spawned - the same
 * deadlock-avoidance {@code GitClient} uses for a large diff. The last
 * {@link ProcessOutputTail#TAIL_LINES} lines are folded into any failure
 * message (D-57: the {@code MINION_DIED} cascade's real trigger, a {@code
 * NoClassDefFoundError} in the driver, was visible only because M2's spike
 * happened to log stderr).
 *
 * <p>D-64 adds two things that tail alone could not give a real dogfood run:
 * a live progress counter fed by {@link MutationDriver}'s per-class {@link
 * ProgressMarker} lines, so a module stuck before the mutation phase is
 * visibly stuck at 0; and, under {@code --diagnostics-dir}, a verbatim copy
 * of the whole (verbose) subprocess log, which is the only way to see why
 * PIT's own coverage minion died.
 */
public final class MutationRunner {

    /**
     * D-59: kept deliberately short, not generous. `-Pmutation-it`
     * dogfooding found this environment's process count climbing past 60
     * within ~2 minutes for a single diff-scoped target under {@code
     * setFullMutationMatrix(true)} - root cause not isolated (full-matrix
     * mode's own cost, or PIT's per-unit minion spawn rate at {@code
     * numberOfThreads=1}, are both plausible and undistinguished). Until
     * that is understood, this budget errs toward failing fast and letting
     * {@link SubprocessWorkspace#destroyProcessTree} clean up a bounded
     * mess rather than an unbounded one - {@code --mutation-timeout} is
     * there for a caller who has verified their own environment handles a
     * longer run safely.
     */
    public static final Duration DEFAULT_BUDGET = Duration.ofMinutes(5);

    private static final String TEMP_DIR_PREFIX = "coverdict-mutation-";

    /**
     * How often a still-running module reports progress. Long enough not to
     * spam a terminal over a 30-minute budget, short enough that a user
     * watching a real run sees movement (or the lack of it) well before
     * the budget expires.
     */
    private static final Duration HEARTBEAT = Duration.ofSeconds(30);

    private static final String PROGRESS_PREFIX = "mutation: module '";

    private MutationRunner() {
    }

    /**
     * @param classPathElements the full PIT-ready classpath (module output
     *                          directories first, then dependency jars).
     * @param codePaths         the subset of {@code classPathElements} that
     *                          are the module's own compiled-output
     *                          directories (never a dependency jar).
     * @param targetClasses     FQCN globs for the changed production classes
     *                          this run should mutate.
     * @param budget            wall-clock budget before the process is force-killed.
     * @param diagnostics       where progress and subprocess logs go.
     * @return evidence if the driver produced its output file before the
     *         budget elapsed and exited cleanly; empty if the run found no
     *         mutable target (PIT's own skip, not a failure) - the two are
     *         distinguished by exit code.
     * @throws MutationCollectionException on process failure, budget
     *         exhaustion, a non-zero exit, or a malformed output file.
     */
    public static Optional<MutationModuleEvidence> run(String moduleId, Path repoRoot,
                                                         List<String> classPathElements,
                                                         List<String> codePaths,
                                                         List<String> targetClasses,
                                                         Duration budget,
                                                         EvidenceDiagnostics diagnostics) {
        Path workDir = createWorkDir(moduleId);
        try (Writer log = diagnostics.openLog(moduleId, "mutation")) {
            List<String> ownClasspathEntries = SubprocessWorkspace.ownRuntimeClasspathEntries();
            List<String> classPathWithSelf = new ArrayList<>(classPathElements);
            classPathWithSelf.addAll(ownClasspathEntries);
            Path classpathFile = writeLines(moduleId, workDir, "classpath.txt", classPathWithSelf);
            Path codePathsFile = writeLines(moduleId, workDir, "codepaths.txt", codePaths);
            Path targetClassesFile = writeLines(moduleId, workDir, "targetclasses.txt", targetClasses);
            Path outputFile = workDir.resolve(CoverdictMutationListener.OUTPUT_FILE_NAME);
            Path classpathArgFile = writeClasspathArgFile(moduleId, workDir, ownClasspathEntries);

            ProcessBuilder pb = new ProcessBuilder(
                SubprocessWorkspace.javaExecutable(), "@" + classpathArgFile,
                MutationDriver.class.getName(),
                moduleId, workDir.toString(), classpathFile.toString(), codePathsFile.toString(),
                targetClassesFile.toString(), String.valueOf(diagnostics.verbose()));
            pb.directory(repoRoot.toFile());
            // One merged stream: progress markers (stdout) and PIT's own
            // java.util.logging output (stderr) end up in one chronological
            // log, drained by one thread with no writer contention.
            pb.redirectErrorStream(true);

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                throw new MutationCollectionException("Could not start the mutation subprocess for module '"
                    + moduleId + "'", e);
            }

            AtomicInteger classesDone = new AtomicInteger();
            ProcessOutputTail output = ProcessOutputTail.of(process.getInputStream(), log,
                line -> ProgressMarker.parse(line).ifPresent(classesDone::set));
            Thread outputThread = output.start("coverdict-mutation-output");

            return awaitAndRead(new Run(moduleId, process, outputThread, output, outputFile, budget,
                targetClasses.size(), classesDone, diagnostics));
        } catch (IOException e) {
            throw new MutationCollectionException("Could not write the diagnostics log for module '" + moduleId + "'", e);
        } finally {
            SubprocessWorkspace.deleteQuietly(workDir);
        }
    }

    public static Optional<MutationModuleEvidence> run(String moduleId, Path repoRoot,
                                                         List<String> classPathElements,
                                                         List<String> codePaths,
                                                         List<String> targetClasses,
                                                         Duration budget) {
        return run(moduleId, repoRoot, classPathElements, codePaths, targetClasses, budget,
            EvidenceDiagnostics.none());
    }

    public static Optional<MutationModuleEvidence> run(String moduleId, Path repoRoot,
                                                         List<String> classPathElements,
                                                         List<String> codePaths,
                                                         List<String> targetClasses) {
        return run(moduleId, repoRoot, classPathElements, codePaths, targetClasses, DEFAULT_BUDGET);
    }

    /** Everything {@link #awaitAndRead} needs about one module's in-flight run (SonarQube java:S107 - the parameter list had outgrown a readable signature). */
    private record Run(String moduleId, Process process, Thread outputThread, ProcessOutputTail output,
                        Path outputFile, Duration budget, int targetCount, AtomicInteger classesDone,
                        EvidenceDiagnostics diagnostics) {
    }

    private static Optional<MutationModuleEvidence> awaitAndRead(Run run) {
        boolean finished = awaitWithProgress(run);
        if (!finished) {
            SubprocessWorkspace.destroyProcessTree(run.process());
            ProcessOutputTail.joinQuietly(run.outputThread());
            run.diagnostics().progress(PROGRESS_PREFIX + run.moduleId() + "' - FAILED, budget of "
                + run.budget().toSeconds() + "s exhausted after " + progressCount(run) + " completed");
            throw new MutationCollectionException("Module '" + run.moduleId() + "' mutation run exceeded its "
                + run.budget().toSeconds() + "s budget (" + progressCount(run) + " completed)"
                + run.output().tailMessage(), MutationCollectionException.BUDGET_EXCEEDED);
        }
        ProcessOutputTail.joinQuietly(run.outputThread());

        int exitCode = run.process().exitValue();
        if (!Files.exists(run.outputFile())) {
            if (exitCode == 0) {
                run.diagnostics().progress(PROGRESS_PREFIX + run.moduleId()
                    + "' - no mutable target found by the engine");
                return Optional.empty(); // no mutable target found - PIT's own skip, not a failure
            }
            run.diagnostics().progress(PROGRESS_PREFIX + run.moduleId() + "' - FAILED, subprocess exited "
                + exitCode + " after " + progressCount(run) + " completed");
            throw new MutationCollectionException("Module '" + run.moduleId() + "' mutation subprocess exited "
                + exitCode + " (" + progressCount(run) + " completed)" + run.output().tailMessage());
        }
        try (InputStream in = Files.newInputStream(run.outputFile())) {
            MutationModuleEvidence evidence = MutationJsonReader.read(in);
            run.diagnostics().progress(PROGRESS_PREFIX + run.moduleId() + "' - done, "
                + evidence.methods().size() + " method(s) with mutants");
            return Optional.of(evidence);
        } catch (IOException e) {
            throw new MutationCollectionException("Module '" + run.moduleId()
                + "' produced an unreadable mutation result file", e);
        }
    }

    /**
     * Waits for the process, emitting a progress line every {@link
     * #HEARTBEAT} until it exits or the budget runs out. The counter comes
     * from {@link MutationDriver}'s markers, so a run still in PIT's
     * coverage phase reports 0 completed - the distinction between "slow
     * mutation phase" and "never reached the mutation phase" that the WTA
     * dogfood had no way to make.
     *
     * @return true if the process exited within its budget.
     */
    private static boolean awaitWithProgress(Run run) {
        long start = System.nanoTime();
        long deadline = start + run.budget().toNanos();
        try {
            while (System.nanoTime() < deadline) {
                long remaining = Math.min(HEARTBEAT.toNanos(), deadline - System.nanoTime());
                if (run.process().waitFor(Math.max(1, remaining / 1_000_000L), TimeUnit.MILLISECONDS)) {
                    return true;
                }
                run.diagnostics().progress(PROGRESS_PREFIX + run.moduleId() + "' - " + progressCount(run)
                    + ", " + ProgressMarker.formatElapsed(Duration.ofNanos(System.nanoTime() - start)) + " elapsed");
            }
            return !run.process().isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SubprocessWorkspace.destroyProcessTree(run.process());
            ProcessOutputTail.joinQuietly(run.outputThread());
            throw new MutationCollectionException("Interrupted while waiting for module '" + run.moduleId() + "'", e);
        }
    }

    /** {@code "42/219 class(es)"} - the denominator is the requested target-class count, which is exact. */
    private static String progressCount(Run run) {
        return run.classesDone().get() + "/" + run.targetCount() + " class(es)";
    }

    private static Path createWorkDir(String moduleId) {
        try {
            return SubprocessWorkspace.createPrivateTempDirectory(TEMP_DIR_PREFIX, moduleId);
        } catch (UncheckedIOException e) {
            throw new MutationCollectionException("Could not create a temp directory for module '" + moduleId + "'", e);
        }
    }

    private static Path writeLines(String moduleId, Path dir, String name, List<String> lines) {
        try {
            return SubprocessWorkspace.writeLines(dir, name, lines);
        } catch (UncheckedIOException e) {
            throw new MutationCollectionException("Could not write '" + name + "' for module '" + moduleId + "'", e);
        }
    }

    private static Path writeClasspathArgFile(String moduleId, Path dir, List<String> classpathEntries) {
        try {
            return SubprocessWorkspace.writeClasspathArgFile(dir, "driver-cp.args", classpathEntries);
        } catch (UncheckedIOException e) {
            throw new MutationCollectionException("Could not write the driver classpath arg file for module '" + moduleId + "'", e);
        }
    }
}
