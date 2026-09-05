package dev.proofjava.analysis.subprocess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Drains one stream of a PIT-driving subprocess on its own thread (deadlock
 * avoidance, the same reason {@code GitClient} gobbles git's output) while
 * keeping only the last {@link #TAIL_LINES} lines in memory - a stuck PIT
 * minion can log for a whole budget window.
 *
 * <p>Extracted from {@code MutationRunner}'s private {@code StderrTail} so
 * {@link dev.proofjava.analysis.pertest.PerTestRunner} can stop discarding
 * its child's output outright (D-64): an L2 run that produced zero evidence
 * was undiagnosable because nothing about the child was ever observed.
 * Same extraction precedent as {@link ClasspathListFile}, which became
 * shared once two callers needed identical parsing.
 *
 * <p>Two optional outputs beyond the in-memory tail, both null/absent by
 * default so the original behaviour is preserved byte for byte:
 *
 * <ul>
 *   <li>a {@code tee} {@link Writer} receiving every line - this is what
 *       {@code --diagnostics-dir} writes its per-module log file with,
 *       since PIT logs through {@code java.util.logging} whose default
 *       console handler already targets stderr;</li>
 *   <li>a {@code lineSink} {@link Consumer} called for every line as it
 *       arrives - used to relay live progress markers to the CLI's own
 *       stderr while the run is still going.</li>
 * </ul>
 *
 * <p>Neither optional output may fail the run: a broken tee writer or a
 * throwing sink is swallowed, exactly like the pre-existing read-failure
 * handling. These are diagnostics, never evidence.
 */
public final class ProcessOutputTail implements Runnable {

    /** Kept small on purpose: this is a failure-message tail, not a log. Full output goes to the tee writer. */
    public static final int TAIL_LINES = 20;

    /**
     * A total-character ceiling on {@link #tailMessage()}, independent of
     * {@link #TAIL_LINES}. PIT's own logging emits one line per record, but a
     * "line" can itself be enormous - a single {@code detected = KILLED by
     * [...]} record lists every killing test, and a high-fan-out method (a
     * gson mutation run stalled on one with 2 201 relevant tests) can turn 20
     * such lines into a gigabyte-scale failure message, ending up embedded
     * verbatim in the verdict JSON. {@link #TAIL_LINES} bounds record count,
     * not record size; this bounds the message actually produced.
     */
    static final int MAX_TAIL_MESSAGE_CHARS = 4_000;

    private final InputStream in;
    private final Writer tee;
    private final Consumer<String> lineSink;
    private final Deque<String> tail = new ArrayDeque<>(TAIL_LINES);

    private ProcessOutputTail(InputStream in, Writer tee, Consumer<String> lineSink) {
        this.in = in;
        this.tee = tee;
        this.lineSink = lineSink;
    }

    /** Tail only - the original {@code MutationRunner.StderrTail} behaviour. */
    public static ProcessOutputTail tailOnly(InputStream in) {
        return new ProcessOutputTail(in, null, null);
    }

    /**
     * @param tee      copied every line, or null; never closed here - the caller owns it.
     * @param lineSink called with every line, or null.
     */
    public static ProcessOutputTail of(InputStream in, Writer tee, Consumer<String> lineSink) {
        return new ProcessOutputTail(in, tee, lineSink);
    }

    /** Starts this drain on its own daemon-free thread and returns it, already running. */
    public Thread start(String threadName) {
        Thread thread = new Thread(this, threadName);
        thread.start();
        return thread;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                accept(line);
            }
        } catch (IOException ignored) {
            // best-effort diagnostics only - never worth failing the run over
        }
    }

    private void accept(String line) {
        if (tail.size() == TAIL_LINES) {
            tail.removeFirst();
        }
        tail.addLast(line);
        writeTee(line);
        notifySink(line);
    }

    private void writeTee(String line) {
        if (tee == null) {
            return;
        }
        try {
            tee.write(line);
            tee.write(System.lineSeparator());
            tee.flush(); // a run killed at its budget must still leave a readable log behind
        } catch (IOException ignored) {
            // diagnostics only
        }
    }

    private void notifySink(String line) {
        if (lineSink == null) {
            return;
        }
        try {
            lineSink.accept(line);
        } catch (RuntimeException ignored) {
            // diagnostics only
        }
    }

    /**
     * {@code " (output tail:\n...)"}, or empty when nothing was captured -
     * folded into a failure message verbatim. Says "output", not "stderr":
     * callers merge the child's two streams into one (D-64), so a captured
     * line may have come from either.
     */
    public String tailMessage() {
        if (tail.isEmpty()) {
            return "";
        }
        String joined = String.join("\n", tail);
        if (joined.length() <= MAX_TAIL_MESSAGE_CHARS) {
            return " (output tail:\n" + joined + ")";
        }
        // Keep the newest content - a stalled run's last words matter most -
        // and say plainly that older lines (or part of one huge line) were cut.
        String kept = joined.substring(joined.length() - MAX_TAIL_MESSAGE_CHARS);
        return " (output tail, truncated to the last " + MAX_TAIL_MESSAGE_CHARS + " chars"
            + " - one or more captured lines were far larger than a failure message should embed:\n" + kept + ")";
    }

    /** Joins {@code thread}, restoring the interrupt flag rather than propagating. */
    public static void joinQuietly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
