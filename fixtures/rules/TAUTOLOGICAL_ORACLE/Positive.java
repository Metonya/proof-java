package rules.tautological_oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// expect: finding method=constantVsConstant confidence=HIGH
// expect: finding method=literalTrue confidence=HIGH
// expect: finding method=literalFalse confidence=HIGH
// expect: finding method=selfComparisonSimpleName confidence=HIGH
// expect: finding method=freshObjectNotNull confidence=HIGH
// expect: finding method=selfComparisonArrayAccess confidence=MEDIUM
class Positive {

    static class Box { }

    @Test
    void constantVsConstant() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void literalTrue() {
        assertTrue(true);
    }

    @Test
    void literalFalse() {
        assertFalse(false);
    }

    @Test
    void selfComparisonSimpleName() {
        int x = compute();
        assertEquals(x, x);
    }

    @Test
    void freshObjectNotNull() {
        assertNotNull(new Box());
    }

    @Test
    void selfComparisonArrayAccess() {
        int[] values = { compute() };
        assertEquals(values[0], values[0]);
    }

    private int compute() { return 42; }
}
