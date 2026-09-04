package dev.proofjava.analysis.subprocess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * How an evidence-collecting run reports what it is doing while it does it
 * (D-64). Threaded from {@code AnalyzeCommand} down through both PIT
 * collectors, so neither {@code MutationRunner} nor {@code PerTestRunner}
 * has to know where the messages end up.
 *
 * <p>Two independent channels, matching two different questions the WTA
 * dogfood could not answer:
 *
 * <ul>
 *   <li><b>progress</b> - always on, written to the CLI's own stderr as the
 *       run proceeds. Answers "where in this are we?": a module whose
 *       counter sits at 0/219 for thirty minutes never reached the
 *       mutation phase at all, which is a completely different problem
 *       from a mutation phase that is merely slow. Deliberately stderr,
 *       never stdout: stdout carries the text report, and the verdict JSON
 *       must stay byte-deterministic (hard rule 7).</li>
 *   <li><b>log directory</b> - opt-in via {@code --diagnostics-dir}. Turns
 *       the PIT drivers verbose and captures every line the subprocess
 *       writes. Answers "why did it fail?": PIT's own minion-crash message
 *       tells the user to enable verbose logging before reporting an
 *       issue, which until now coverdict had no way to do.</li>
 * </ul>
 *
 * @param logDir       directory for per-module subprocess logs, or null for none.
 * @param progressSink where a human-readable progress line goes; never null
 *                     (use {@link #none()} for a run that reports nothing).
 */
public record EvidenceDiagnostics(Path logDir, Consumer<String> progressSink) {

    private static final EvidenceDiagnostics NONE = new EvidenceDiagnostics(null, message -> { });

    /** Neither channel - the shape used by tests and by any caller with nowhere to report. */
    public static EvidenceDiagnostics none() {
        return NONE;
    }

    /** Progress only, no subprocess log capture - the default for a run without {@code --diagnostics-dir}. */
    public static EvidenceDiagnostics progressOnly(Consumer<String> progressSink) {
        return new EvidenceDiagnostics(null, progressSink);
    }

    /**
     * Whether the PIT drivers should run verbose. Tied to {@link #logDir}
     * rather than being separately switchable: verbose output with nowhere
     * to put it is just a slower run, since the in-memory tail only ever
     * keeps {@link ProcessOutputTail#TAIL_LINES} lines either way.
     */
    public boolean verbose() {
        return logDir != null;
    }

    /** Reports one progress line; the sink itself decides how to render it. */
    public void progress(String message) {
        progressSink.accept(message);
    }

    /**
     * Opens {@code <logDir>/<moduleId>-<layer>.log}, or returns null when no
     * log directory was requested. The caller closes it.
     *
     * <p>A log that cannot be opened is reported through {@link #progress}
     * and degrades to null rather than failing the run - diagnostics never
     * cost evidence (hard rule 3a is about never reporting missing evidence
     * as success, not about making a logging problem fatal).
     *
     * @param layer {@code "mutation"} or {@code "pertest"}.
     */
    public Writer openLog(String moduleId, String layer) {
        if (logDir == null) {
            return null;
        }
        Path file = logDir.resolve(sanitize(moduleId) + "-" + layer + ".log");
        try {
            Files.createDirectories(logDir);
            return Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException e) {
            progress("could not open diagnostics log " + file + " (" + e.getClass().getSimpleName()
                + "); continuing without it");
            return null;
        }
    }

    /** Same rule as {@link SubprocessWorkspace}'s temp-directory naming - a module id is a CLI-supplied string. */
    private static String sanitize(String moduleId) {
        return moduleId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
