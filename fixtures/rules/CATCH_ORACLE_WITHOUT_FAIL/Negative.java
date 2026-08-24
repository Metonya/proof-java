package rules.catch_oracle_without_fail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.truth.Truth;
import org.junit.jupiter.api.Test;

// expect: none method=tryWithFail
// expect: none method=catchRethrows
// expect: none method=tryWithResourcesNoCatch
// expect: none method=assertThrowsIdiom
// expect: none method=oracleAfterTry
// expect: none method=truthOracleAfterCapturedTry
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

    @Test
    void oracleAfterTry() {
        // README: "Never fires on: Oracles present after the whole try statement" -
        // the catch itself only captures, but a real oracle follows the try/catch.
        AtomicReference<RuntimeException> captured = new AtomicReference<>();
        try {
            new Parser().parse("not-a-number");
        } catch (NumberFormatException e) {
            captured.set(e);
        }
        assertEquals(true, captured.get() != null);
    }

    @Test
    void truthOracleAfterCapturedTry() {
        // Same shape, but every oracle - inside and after the try - is a Truth
        // call (D-31): proves the rule's own documented exemption engages once
        // Truth is a recognized oracle, not just for JUnit/AssertJ.
        AtomicReference<RuntimeException> captured = new AtomicReference<>();
        try {
            Truth.assertThat(new Parser().parse("7")).isEqualTo(7);
        } catch (RuntimeException e) {
            captured.set(e);
        }
        Truth.assertThat(captured.get()).isNull();
    }
}
