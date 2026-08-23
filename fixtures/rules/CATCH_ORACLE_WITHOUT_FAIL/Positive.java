package rules.catch_oracle_without_fail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// expect: finding method=emptyCatch confidence=HIGH
// expect: finding method=catchOnlyLogs confidence=HIGH
// expect: finding method=catchWithNonLoggingCall confidence=MEDIUM
class Positive {

    static class Parser {
        int parse(String s) { return Integer.parseInt(s); }
    }

    static class Metrics {
        void increment(String name) { }
    }

    @Test
    void emptyCatch() {
        try {
            int v = new Parser().parse("41");
            assertEquals(41, v);
        } catch (Exception e) {
        }
    }

    @Test
    void catchOnlyLogs() {
        try {
            int v = new Parser().parse("42");
            assertEquals(42, v);
        } catch (Exception e) {
            System.out.println("ignored: " + e.getMessage());
        }
    }

    @Test
    void catchWithNonLoggingCall() {
        Metrics metrics = new Metrics();
        try {
            int v = new Parser().parse("43");
            assertEquals(43, v);
        } catch (Exception e) {
            metrics.increment("parse.failure");
        }
    }
}
