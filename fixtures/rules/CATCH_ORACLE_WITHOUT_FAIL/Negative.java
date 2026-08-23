package rules.catch_oracle_without_fail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

// expect: none method=tryWithFail
// expect: none method=catchRethrows
// expect: none method=tryWithResourcesNoCatch
// expect: none method=assertThrowsIdiom
class Negative {

    static class Parser {
        int parse(String s) { return Integer.parseInt(s); }
    }

    @Test
    void tryWithFail() {
        try {
            new Parser().parse("not-a-number");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    @Test
    void catchRethrows() {
        try {
            assertEquals(7, new Parser().parse("7"));
        } catch (RuntimeException e) {
            throw new IllegalStateException("parse failed", e);
        }
    }

    @Test
    void tryWithResourcesNoCatch() throws Exception {
        try (StringReader reader = new StringReader("8")) {
            assertEquals(56, reader.read());
        }
    }

    @Test
    void assertThrowsIdiom() {
        assertThrows(NumberFormatException.class, () -> new Parser().parse("x"));
    }
}
