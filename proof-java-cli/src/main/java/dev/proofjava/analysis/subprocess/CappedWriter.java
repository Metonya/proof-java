package dev.proofjava.analysis.subprocess;

import java.io.IOException;
import java.io.Writer;

/**
 * A {@link Writer} that stops after a byte budget and says so, instead of
 * growing without bound (D-89).
 *
 * <p>Written for the verbose PIT subprocess logs: one of them reached 184 MB for
 * a single module on google/gson, since verbose logging emits a line per mutant
 * per test. Those logs exist to explain why a subprocess misbehaved, and the
 * useful part is the run's start - the invocation, the classpath, the first
 * failure - so the cap keeps the head and drops the tail.
 *
 * <p>Silently truncating would be its own small lie, so the last thing written
 * is a line saying the log was cut and how much was dropped.
 */
final class CappedWriter extends Writer {

    private final Writer delegate;
    private final long maxBytes;
    private long written;
    private long dropped;
    private boolean noticeWritten;

    CappedWriter(Writer delegate, long maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    @Override
    public void write(char[] buffer, int offset, int length) throws IOException {
        // An approximation of the encoded size: exact byte accounting would mean
        // encoding twice, and the cap is a safety limit, not a quota.
        long size = (long) length * 2;
        if (written + size > maxBytes) {
            dropped += size;
            writeTruncationNotice();
            return;
        }
        written += size;
        delegate.write(buffer, offset, length);
    }

    private void writeTruncationNotice() throws IOException {
        if (noticeWritten) {
            return;
        }
        noticeWritten = true;
        String notice = System.lineSeparator()
            + "--- diagnostics log truncated at " + (maxBytes / (1024 * 1024)) + " MB by proof-java (D-89). "
            + "The engine kept running; only this log stopped. ---" + System.lineSeparator();
        delegate.write(notice);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        if (noticeWritten && dropped > 0) {
            delegate.write("--- approximately " + (dropped / (1024 * 1024)) + " MB not written ---"
                + System.lineSeparator());
        }
        delegate.close();
    }
}
