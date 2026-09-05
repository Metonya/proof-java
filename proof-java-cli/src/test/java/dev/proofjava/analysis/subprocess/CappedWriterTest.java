package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class CappedWriterTest {

    @Test
    void writesEverythingWhileUnderTheCap() throws IOException {
        StringWriter sink = new StringWriter();
        try (CappedWriter writer = new CappedWriter(sink, 1024)) {
            writer.write("a line that fits\n");
        }

        assertTrue(sink.toString().startsWith("a line that fits"), sink.toString());
        assertTrue(!sink.toString().contains("truncated"), "nothing was dropped, so nothing to announce");
    }

    /**
     * The point of the cap: a verbose PIT log reached 184 MB for one module on
     * google/gson. Truncating silently would leave a cut-off file that reads as
     * a complete one, so the notice matters as much as the limit.
     */
    @Test
    void stopsAtTheCapAndSaysThatItDid() throws IOException {
        StringWriter sink = new StringWriter();
        try (CappedWriter writer = new CappedWriter(sink, 64)) {
            writer.write("head that fits\n");
            for (int i = 0; i < 100; i++) {
                writer.write("a long line that should be dropped once the budget is gone\n");
            }
        }

        String written = sink.toString();
        assertTrue(written.startsWith("head that fits"), "the head is what explains the run: " + written);
        assertTrue(written.contains("truncated"), "a truncated log must say so: " + written);
        assertTrue(written.length() < 1000, "the cap did not hold, wrote " + written.length() + " chars");
    }
}
