package dev.proofjava.doctor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.proofjava.analysis.subprocess.ProcessOutputTail;

/**
 * Invokes a Gradle build's own wrapper ({@code ./gradlew}/{@code
 * gradlew.bat}) as an argv array, never through a shell - same discipline
 * as {@link MavenClient} (SECURITY-POLICY.md #3). Deliberately requires
 * the wrapper rather than a bare {@code gradle} on PATH: the wrapper pins
 * the exact Gradle version the target repo was built against (a bare
 * {@code gradle} could silently be a different major version, with
 * different Isolated Projects/Configuration Cache defaults - the two
 * obstacles {@link GradleClasspathFixer}'s init script has to work around,
 * D-53), and every real Gradle project this class needs to work against
 * already commits its wrapper.
 *
 * <p>Same scoping as {@link MavenClient}: confined to {@code doctor
 * --fix}, never called from {@code analyze} (D-65's "verdict layer, never
 * own engines" boundary).
 */
public class GradleClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final Path repoRoot;
    private final Duration timeout;

    public GradleClient(Path repoRoot) {
        this(repoRoot, DEFAULT_TIMEOUT);
    }

    GradleClient(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    /** @return true if this repo has a committed Gradle wrapper - the only entry point this client will invoke. */
    public boolean hasWrapper() {
        return Files.isRegularFile(wrapperScript());
    }

    public Result run(String... args) {
        if (!hasWrapper()) {
            return new Result(false, "no Gradle wrapper (gradlew"
                + (isWindows() ? ".bat" : "") + ") found at the repo root - proof-java only ever invokes a "
                + "project's own pinned wrapper, never a bare 'gradle' on PATH");
        }

        List<String> command = new ArrayList<>(args.length + 1);
        command.add(wrapperScript().toString());
        command.addAll(List.of(args));

        Process process;
        try {
            process = new ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start();
        } catch (IOException e) {
            return new Result(false, "could not start the Gradle wrapper: " + e.getMessage());
        }

        ProcessOutputTail output = ProcessOutputTail.tailOnly(process.getInputStream());
        Thread outputThread = output.start("proof-doctor-gradle-output");

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            ProcessOutputTail.joinQuietly(outputThread);
            return new Result(false, "interrupted while waiting for gradlew " + String.join(" ", args));
        }
        if (!finished) {
            process.destroyForcibly();
            ProcessOutputTail.joinQuietly(outputThread);
            return new Result(false, "gradlew " + String.join(" ", args) + " exceeded its " + timeout.toSeconds()
                + "s timeout" + output.tailMessage());
        }
        ProcessOutputTail.joinQuietly(outputThread);
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return new Result(false, "gradlew " + String.join(" ", args) + " exited " + exitCode + output.tailMessage());
        }
        return new Result(true, null);
    }

    private Path wrapperScript() {
        return repoRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /** @param problem null on success. */
    public record Result(boolean ok, String problem) {
    }
}
