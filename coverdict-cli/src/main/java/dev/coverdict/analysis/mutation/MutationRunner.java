package dev.coverdict.analysis.mutation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import dev.coverdict.analysis.subprocess.SubprocessWorkspace;

/**
 * Spawns {@link MutationDriver} as a genuinely separate OS process and lets
 * it run to completion, bounded by a budget - L3's sibling of {@link
 * dev.coverdict.analysis.pertest.PerTestRunner}, but never force-killed on
 * the happy path: unlike L2 (which only needs the coverage phase and force
 * -kills the moment it's done), the mutation phase itself is the evidence
 * this collects, so it must be allowed to actually finish.
 *
 * <p>Stderr is drained on a dedicated thread from the moment the process is
 * spawned - the same deadlock-avoidance {@code GitClient} uses for a large
 * diff - and its last {@value #STDERR_TAIL_LINES} lines are folded into any
 * failure message, closing the diagnosability gap M2's spike logs left
 * (D-57: the {@code MINION_DIED} cascade's real trigger, a {@code
 * NoClassDefFoundError} in the driver, was visible only because the spike
 * happened to log stderr - {@link dev.coverdict.analysis.pertest.PerTestRunner}
 * discards it outright).
 */
public final class MutationRunner {

    /**
     * A mutation run's own test-suite execution is not diff-scoped (only
     * the mutant targets are, D-56/O-05) - PIT still discovers and runs
     * every test that might cover a target class, so this budget is
     * generous relative to L2's 120s coverage-only window.
     */
    public static final Duration DEFAULT_BUDGET = Duration.ofMinutes(15);

    private static final String TEMP_DIR_PREFIX = "coverdict-mutation-";
    private static final int STDERR_TAIL_LINES = 20;

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
     * @return evidence if the driver produced its output file before the
     *         budget elapsed and exited cleanly; empty if the run found no
     *         mutable target (PIT's own skip, not a failure) - the two are
     *         distinguished by exit code, exactly like {@code PerTestRunner}
     *         cannot (it never reads one).
     * @throws MutationCollectionException on process failure, budget
     *         exhaustion, a non-zero exit, or a malformed output file.
     */
    public static Optional<MutationModuleEvidence> run(String moduleId, Path repoRoot,
                                                         List<String> classPathElements,
                                                         List<String> codePaths,
                                                         List<String> targetClasses,
                                                         Duration budget) {
        Path workDir = createWorkDir(moduleId);
        try {
            String ownClasspath = ownClasspath();
            List<String> classPathWithSelf = new ArrayList<>(classPathElements);
            classPathWithSelf.add(ownClasspath);
            Path classpathFile = writeLines(moduleId, workDir, "classpath.txt", classPathWithSelf);
            Path codePathsFile = writeLines(moduleId, workDir, "codepaths.txt", codePaths);
            Path targetClassesFile = writeLines(moduleId, workDir, "targetclasses.txt", targetClasses);
            Path outputFile = workDir.resolve(CoverdictMutationListener.OUTPUT_FILE_NAME);

            ProcessBuilder pb = new ProcessBuilder(
                SubprocessWorkspace.javaExecutable(), "-cp", ownClasspath,
                MutationDriver.class.getName(),
                moduleId, workDir.toString(), classpathFile.toString(), codePathsFile.toString(),
                targetClassesFile.toString());
            pb.directory(repoRoot.toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                throw new MutationCollectionException("Could not start the mutation subprocess for module '"
                    + moduleId + "'", e);
            }

            StderrTail stderr = new StderrTail(process.getErrorStream());
            Thread errThread = new Thread(stderr, "coverdict-mutation-stderr");
            errThread.start();

            return awaitAndRead(moduleId, process, errThread, stderr, outputFile, budget);
        } finally {
            SubprocessWorkspace.deleteQuietly(workDir);
        }
    }

    public static Optional<MutationModuleEvidence> run(String moduleId, Path repoRoot,
                                                         List<String> classPathElements,
                                                         List<String> codePaths,
                                                         List<String> targetClasses) {
        return run(moduleId, repoRoot, classPathElements, codePaths, targetClasses, DEFAULT_BUDGET);
    }

    private static Optional<MutationModuleEvidence> awaitAndRead(String moduleId, Process process, Thread errThread,
                                                                   StderrTail stderr, Path outputFile, Duration budget) {
        boolean finished;
        try {
            finished = process.waitFor(budget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            joinQuietly(errThread);
            throw new MutationCollectionException("Interrupted while waiting for module '" + moduleId + "'", e);
        }
        if (!finished) {
            process.destroyForcibly();
            joinQuietly(errThread);
            throw new MutationCollectionException("Module '" + moduleId + "' mutation run exceeded its "
                + budget.toSeconds() + "s budget" + stderr.tailMessage(), MutationCollectionException.BUDGET_EXCEEDED);
        }
        joinQuietly(errThread);

        int exitCode = process.exitValue();
        if (!Files.exists(outputFile)) {
            if (exitCode == 0) {
                return Optional.empty(); // no mutable target found - PIT's own skip, not a failure
            }
            throw new MutationCollectionException("Module '" + moduleId + "' mutation subprocess exited "
                + exitCode + stderr.tailMessage());
        }
        try (InputStream in = Files.newInputStream(outputFile)) {
            return Optional.of(MutationJsonReader.read(in));
        } catch (IOException e) {
            throw new MutationCollectionException("Module '" + moduleId + "' produced an unreadable mutation result file", e);
        }
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private static String ownClasspath() {
        try {
            return SubprocessWorkspace.ownClasspath(MutationRunner.class);
        } catch (IllegalStateException e) {
            throw new MutationCollectionException("Could not resolve coverdict's own classpath for the mutation subprocess", e);
        }
    }

    /**
     * Drains the child's stderr on its own thread (deadlock avoidance, same
     * reason as {@code GitClient}'s {@code StreamGobbler}) while keeping
     * only the last {@link #STDERR_TAIL_LINES} lines in memory - a stuck
     * PIT minion can log for the full budget window.
     */
    private static final class StderrTail implements Runnable {
        private final InputStream in;
        private final Deque<String> tail = new ArrayDeque<>(STDERR_TAIL_LINES);

        StderrTail(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (tail.size() == STDERR_TAIL_LINES) {
                        tail.removeFirst();
                    }
                    tail.addLast(line);
                }
            } catch (IOException ignored) {
                // best-effort diagnostics only - never worth failing the run over
            }
        }

        String tailMessage() {
            if (tail.isEmpty()) {
                return "";
            }
            return " (stderr tail:\n" + String.join("\n", tail) + ")";
        }
    }
}
