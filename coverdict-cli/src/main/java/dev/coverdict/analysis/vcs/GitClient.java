package dev.coverdict.analysis.vcs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import dev.coverdict.analysis.AnalysisException;
import dev.coverdict.analysis.model.RepoPaths;

/**
 * Invokes the {@code git} binary as an argv array, never through a shell
 * (SECURITY-POLICY.md #3): explicit working directory, the inherited process
 * environment left untouched (no interpolation of repo content into it, no
 * added secrets - "minimal" here means never augmented, not stripped bare,
 * since git itself needs {@code PATH}/{@code HOME} to run at all), a bounded
 * timeout, and UTF-8 decoding of every byte git writes - this machine's
 * platform default is Cp1254 (the same trap {@code --encoding UTF-8} exists
 * to dodge for JaCoCo XML, per docs/M0-CLI-INPUT.md).
 *
 * <p>Every diff command passes {@code --no-color}, explicit {@code
 * --src-prefix=a/ --dst-prefix=b/}, and {@code --find-renames} so output
 * never depends on the caller's own {@code ~/.gitconfig} ({@code color.ui},
 * {@code diff.mnemonicPrefix}, {@code diff.noprefix}, {@code diff.renames}
 * are all real ways a user's global config would otherwise make this tool's
 * output nondeterministic for the exact same repository state).
 *
 * <p>Stdout and stderr are drained on separate threads starting immediately
 * after the process is spawned, before {@code waitFor} is ever called: a
 * diff larger than the OS pipe buffer (tens of KB - trivial for a real
 * changeset) would otherwise deadlock the process against an undrained pipe,
 * and the configured timeout would then misreport that hang as {@code
 * GIT_TIMEOUT} instead of the actual bug.
 */
public final class GitClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern SHA_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

    private final Path repoRoot;
    private final Duration timeout;

    public GitClient(Path repoRoot) {
        this(repoRoot, DEFAULT_TIMEOUT);
    }

    GitClient(Path repoRoot, Duration timeout) {
        this.repoRoot = repoRoot;
        this.timeout = timeout;
    }

    /** @throws AnalysisException(UNRESOLVABLE_HEAD) if HEAD does not resolve to a commit (e.g. a repository with no commits yet). */
    public String resolveHead() {
        ProcessResult r = run("rev-parse", "--verify", "HEAD^{commit}");
        String sha = r.stdout().strip();
        if (r.exitCode() != 0 || !isSha(sha)) {
            throw new AnalysisException("UNRESOLVABLE_HEAD",
                "HEAD does not resolve to a commit in " + repoRoot + " (a repository with no commits yet?): " + r.stderr().strip());
        }
        return sha;
    }

    /** @throws AnalysisException(UNRESOLVABLE_REF) if {@code ref} does not resolve to a commit. */
    public String resolveRef(String ref) {
        ProcessResult r = run("rev-parse", "--verify", ref + "^{commit}");
        String sha = r.stdout().strip();
        if (r.exitCode() != 0 || !isSha(sha)) {
            throw new AnalysisException("UNRESOLVABLE_REF",
                "--base ref '" + ref + "' does not resolve to a commit: " + r.stderr().strip());
        }
        return sha;
    }

    /**
     * @throws AnalysisException(MISSING_MERGE_BASE) if {@code base} and {@code head} share no common
     *         ancestor - exit 1 from git, typically a shallow clone or unrelated histories
     * @throws AnalysisException(GIT_INVOCATION_FAILED) on any other git failure
     */
    public String mergeBase(String base, String head) {
        ProcessResult r = run("merge-base", base, head);
        if (r.exitCode() == 1) {
            throw new AnalysisException("MISSING_MERGE_BASE",
                "No merge base between '" + base + "' and '" + head + "' - a shallow clone or unrelated histories.");
        }
        String sha = r.stdout().strip();
        if (r.exitCode() != 0 || !isSha(sha)) {
            throw gitFailed("merge-base", r);
        }
        return sha;
    }

    /**
     * Tracked changes only - working tree against {@code HEAD}, staged and
     * unstaged alike. Untracked files are reported separately by {@link
     * #untrackedFiles()} and never folded into this flag (D-16 records them
     * as a distinct concern: an untracked Java file can make a run
     * incomplete on its own, independent of whether tracked content is dirty).
     */
    public boolean isDirty() {
        ProcessResult r = run("diff", "--quiet", "HEAD", "--");
        if (r.exitCode() == 0) {
            return false;
        }
        if (r.exitCode() == 1) {
            return true;
        }
        throw gitFailed("diff --quiet", r);
    }

    /** Raw {@code git diff --unified=0} text from {@code fromRev} to the working tree; parse with {@link UnifiedDiffParser}. */
    public String diffUnified0(String fromRev) {
        ProcessResult r = run("diff", "--unified=0", "--find-renames", "--no-color",
            "--src-prefix=a/", "--dst-prefix=b/", fromRev, "--");
        if (r.exitCode() != 0) {
            throw gitFailed("diff", r);
        }
        return r.stdout();
    }

    /** Repo-relative, NUL-delimited so a path containing a newline or quote character is never misparsed. */
    public List<String> untrackedFiles() {
        ProcessResult r = run("ls-files", "-z", "--others", "--exclude-standard");
        if (r.exitCode() != 0) {
            throw gitFailed("ls-files", r);
        }
        if (r.stdout().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(r.stdout().split("\0"))
            .filter(s -> !s.isEmpty())
            .map(RepoPaths::normalizeSeparators)
            .toList();
    }

    private static AnalysisException gitFailed(String operation, ProcessResult r) {
        return new AnalysisException("GIT_INVOCATION_FAILED",
            "git " + operation + " failed (exit " + r.exitCode() + "): " + r.stderr().strip());
    }

    private static boolean isSha(String s) {
        return SHA_PATTERN.matcher(s).matches();
    }

    private ProcessResult run(String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-c");
        command.add("core.quotepath=false");
        command.addAll(Arrays.asList(args));

        Process process;
        try {
            process = new ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .start();
        } catch (IOException e) {
            throw new AnalysisException("GIT_INVOCATION_FAILED", "Could not start git: " + e.getMessage(), e);
        }

        StreamGobbler stdout = new StreamGobbler(process.getInputStream());
        StreamGobbler stderr = new StreamGobbler(process.getErrorStream());
        Thread outThread = new Thread(stdout, "coverdict-git-stdout");
        Thread errThread = new Thread(stderr, "coverdict-git-stderr");
        outThread.start();
        errThread.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new AnalysisException("GIT_INVOCATION_FAILED", "Interrupted while waiting for git " + String.join(" ", args), e);
        }
        if (!finished) {
            process.destroyForcibly();
            joinQuietly(outThread);
            joinQuietly(errThread);
            throw new AnalysisException("GIT_TIMEOUT",
                "git " + String.join(" ", args) + " did not finish within " + timeout.getSeconds() + "s.");
        }
        joinQuietly(outThread);
        joinQuietly(errThread);

        return new ProcessResult(process.exitValue(),
            new String(stdout.bytes(), StandardCharsets.UTF_8),
            new String(stderr.bytes(), StandardCharsets.UTF_8));
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * Reads an entire stream on its own thread so a large diff can never
     * deadlock the pipe (see class javadoc). No {@code volatile} needed on
     * {@link #bytes}: every caller reads it only after {@code Thread.join()}
     * on this runnable's thread, and join() already establishes a
     * happens-before edge with everything the thread did before it finished.
     */
    private static final class StreamGobbler implements Runnable {
        private final InputStream in;
        private byte[] bytes = new byte[0];

        StreamGobbler(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            try {
                bytes = in.readAllBytes();
            } catch (IOException e) {
                bytes = ("<stream read failed: " + e.getMessage() + ">").getBytes(StandardCharsets.UTF_8);
            }
        }

        byte[] bytes() {
            return bytes;
        }
    }
}
