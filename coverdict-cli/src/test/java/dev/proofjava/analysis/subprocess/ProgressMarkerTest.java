package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** D-64: the child-to-parent progress protocol, and the elapsed-time rendering that goes beside it. */
class ProgressMarkerTest {

    @Test
    void roundTripsACount() {
        assertEquals(42, ProgressMarker.parse(ProgressMarker.PREFIX + "42").orElseThrow());
    }

    @Test
    void ignoresAnOrdinaryLogLine() {
        assertTrue(ProgressMarker.parse("PIT >> INFO : mutating class Foo").isEmpty());
        assertTrue(ProgressMarker.parse("").isEmpty());
        assertTrue(ProgressMarker.parse(null).isEmpty());
    }

    /** A log line that merely starts with the prefix but carries no number must not be read as progress. */
    @Test
    void ignoresAMalformedMarker() {
        assertTrue(ProgressMarker.parse(ProgressMarker.PREFIX + "not-a-number").isEmpty());
    }

    @Test
    void theMarkerPrefixCannotCollideWithEngineOutput() {
        assertTrue(ProgressMarker.PREFIX.startsWith("##"), "no logging framework prefixes a line with ##");
        assertFalse(ProgressMarker.PREFIX.isBlank());
    }

    @Test
    void formatsElapsedTimeCompactly() {
        assertEquals("0s", ProgressMarker.formatElapsed(Duration.ZERO));
        assertEquals("48s", ProgressMarker.formatElapsed(Duration.ofSeconds(48)));
        assertEquals("6m12s", ProgressMarker.formatElapsed(Duration.ofSeconds(372)));
        assertEquals("30m0s", ProgressMarker.formatElapsed(Duration.ofMinutes(30)));
    }

    /** A clock that appears to go backwards must not render a negative duration. */
    @Test
    void clampsANegativeElapsedTime() {
        assertEquals("0s", ProgressMarker.formatElapsed(Duration.ofSeconds(-5)));
    }
}
