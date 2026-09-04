package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * D-64: the shared drain that replaced {@code MutationRunner}'s private
 * {@code StderrTail}. Covers the three things a caller depends on - a
 * bounded tail, a complete tee, and a line sink that cannot break the run.
 */
class ProcessOutputTailTest {

    private static ByteArrayInputStream lines(String... values) {
        return new ByteArrayInputStream(String.join("\n", values).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void keepsOnlyTheLastLinesInItsTail() {
        String[] many = IntStream.rangeClosed(1, ProcessOutputTail.TAIL_LINES + 10)
            .mapToObj(i -> "line " + i).toArray(String[]::new);

        ProcessOutputTail tail = ProcessOutputTail.tailOnly(lines(many));
        tail.run();

        String message = tail.tailMessage();
        assertTrue(message.contains("line " + (ProcessOutputTail.TAIL_LINES + 10)), "keeps the newest line");
        assertFalse(message.contains("line 1\n"), "drops the oldest line");
        assertEquals(ProcessOutputTail.TAIL_LINES, message.lines().count() - 1, "one header line plus the tail");
    }

    @Test
    void anEmptyStreamProducesNoTailMessageAtAll() {
        ProcessOutputTail tail = ProcessOutputTail.tailOnly(new ByteArrayInputStream(new byte[0]));
        tail.run();

        assertEquals("", tail.tailMessage());
    }

    @Test
    void teeReceivesEveryLineNotJustTheTail() {
        String[] many = IntStream.rangeClosed(1, ProcessOutputTail.TAIL_LINES + 10)
            .mapToObj(i -> "line " + i).toArray(String[]::new);
        Writer tee = new StringWriter();

        ProcessOutputTail.of(lines(many), tee, null).run();

        List<String> teed = tee.toString().lines().toList();
        assertEquals(ProcessOutputTail.TAIL_LINES + 10, teed.size(), "the log keeps what the tail discards");
        assertEquals("line 1", teed.get(0));
    }

    @Test
    void theLineSinkSeesEveryLine() {
        List<String> seen = new ArrayList<>();

        ProcessOutputTail.of(lines("a", "b", "c"), null, seen::add).run();

        assertEquals(List.of("a", "b", "c"), seen);
    }

    /** Diagnostics must never cost evidence: a sink that throws is swallowed, and the tail still completes. */
    @Test
    void aThrowingLineSinkNeverBreaksTheDrain() {
        ProcessOutputTail tail = ProcessOutputTail.of(lines("a", "b"), null, line -> {
            throw new IllegalStateException("sink blew up");
        });

        tail.run();

        assertTrue(tail.tailMessage().contains("b"));
    }
}
