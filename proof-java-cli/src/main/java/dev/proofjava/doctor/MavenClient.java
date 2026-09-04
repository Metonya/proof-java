package dev.proofjava.doctor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import dev.proofjava.analysis.subprocess.ProcessOutputTail;

/**
 * Invokes the {@code mvn} binary as an argv array, never through a shell -
 * same discipline as {@code GitClient} (SECURITY-POLICY.md #3): explicit
 * working directory, a bounded timeout, output drained on its own thread
 * from the moment the process is spawned ({@link ProcessOutputTail}, D-64's
 * shared drain - the deadlock-avoidance reasoning is identical: Maven's
 * dependency-resolution output can exceed the OS pipe buffer).
 *
 * <p>This is deliberately scoped to {@code doctor --fix} alone. {@code
 * analyze} never calls this class - AGENTS.md's "verdict layer, never own
 * engines" rule stays intact for the analysis path; {@code doctor} is a
 * separate, explicitly opt-in convenience command, not the core engine, and
 * the one command it runs (Maven's own {@code dependency:build-classpath})
 * only ever asks Maven a question, never changes what it built (D-65 -
 * closes the "L2/L3 classpath UX gap" backlog item, ROADMAP.md).
 */
public class MavenClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final Path repoRoot;
    private final Duration timeout;

    public MavenClient(Path repoRoot) {
        this(repoRoot, DEFAULT_TIMEOUT);
    }

    MavenClient(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    /**
     * Runs {@code mvn -pl <moduleRoot> dependency:build-classpath
     * -Dmdep.outputFile=target/coverdict-dependencies.txt
     * -Dmdep.includeScope=test} from the reactor root. The output path is
     * module-relative, not repo-relative - the WTA dogfood's first classpath
     * recipe used a repo-relative path together with {@code -pl}, which
     * changes Maven's own working directory to the module, so the file
     * landed under a duplicated {@code <module>/<module>/...} path
     * (ROADMAP.md, "L2/L3 classpath UX gap"). {@code -Dmdep.outputFile=
     * target/...} is the corrected form the dogfood runbook settled on.
     *
     * @return true on a clean (exit 0) run.
     */
    public Result buildClasspath(String moduleRoot) {
        return run("-pl", moduleRoot, "dependency:build-classpath",
            "-Dmdep.outputFile=target/coverdict-dependencies.txt",
            "-Dmdep.includeScope=test");
    }

    private Result run(String... args) {
        List<String> command = new ArrayList<>(args.length + 1);
        command.add(mavenExecutable());
        command.addAll(List.of(args));

        Process process;
        try {
            process = new ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start();
        } catch (IOException e) {
            return new Result(false, "could not start mvn: " + e.getMessage());
        }

        ProcessOutputTail output = ProcessOutputTail.tailOnly(process.getInputStream());
        Thread outputThread = output.start("coverdict-doctor-mvn-output");

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            ProcessOutputTail.joinQuietly(outputThread);
            return new Result(false, "interrupted while waiting for mvn " + String.join(" ", args));
        }
        if (!finished) {
            process.destroyForcibly();
            ProcessOutputTail.joinQuietly(outputThread);
            return new Result(false, "mvn " + String.join(" ", args) + " exceeded its " + timeout.toSeconds()
                + "s timeout" + output.tailMessage());
        }
        ProcessOutputTail.joinQuietly(outputThread);
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return new Result(false, "mvn " + String.join(" ", args) + " exited " + exitCode + output.tailMessage());
        }
        return new Result(true, null);
    }

    /** {@code mvn.cmd} on Windows, {@code mvn} elsewhere - same PATH-resolved shape {@code javaExecutable()} avoids for a known binary, but Maven's launcher script has no fixed install location to pin the way {@code %SystemRoot%} pins taskkill. */
    private static String mavenExecutable() {
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** @param problem null on success. */
    public record Result(boolean ok, String problem) {
    }
}
