package dev.proofjava.analysis.subprocess;

import java.util.OptionalInt;

/**
 * The one-line protocol a PIT-driving subprocess uses to tell its parent how
 * far it has got (D-64). The child prints {@link #PREFIX} followed by a
 * count on its own stdout; the parent's {@link ProcessOutputTail} line sink
 * recognizes it and updates the live progress counter.
 *
 * <p>A marker line rather than a shared file or a socket because the parent
 * already drains the child's output for its failure tail - this rides that
 * existing channel and costs no new plumbing. The prefix is deliberately
 * unlike anything PIT logs, so an ordinary log line is never misread as
 * progress.
 *
 * <p>Marker lines are still written to a {@code --diagnostics-dir} log
 * verbatim: they are a genuine record of when each class completed.
 */
public final class ProgressMarker {

    public static final String PREFIX = "##proof-progress ";

    private ProgressMarker() {
    }

    /**
     * Called from inside the subprocess. Uses {@code System.out} directly:
     * this class runs in the child JVM, where the parent's CLI writers do
     * not exist.
     */
    public static void emit(int completed) {
        System.out.println(PREFIX + completed);
    }

    /** The count carried by {@code line}, or empty when it is not a marker. */
    public static OptionalInt parse(String line) {
        if (line == null || !line.startsWith(PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(line.substring(PREFIX.length()).trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty(); // a log line that merely happens to share the prefix
        }
    }

    /** {@code "6m12s"} / {@code "48s"} - compact elapsed time for a progress line. */
    public static String formatElapsed(java.time.Duration elapsed) {
        long totalSeconds = Math.max(0, elapsed.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes == 0 ? seconds + "s" : minutes + "m" + seconds + "s";
    }
}
