package rules.tautological_oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// expect: none method=realComparison
// expect: none method=identicalOperandsWithCall
// expect: none method=contractConstantPin
class Negative {

    static class MathLib {
        static final double PI = 3.14159;
        static int next() { return 7; }
        static int add(int a, int b) { return a + b; }
    }

    @Test
    void realComparison() {
        assertEquals(4, MathLib.add(2, 2));
    }

    @Test
    void identicalOperandsWithCall() {
        // side effects unprovable statically - must stay silent
        assertEquals(MathLib.next(), MathLib.next());
    }

    @Test
    void contractConstantPin() {
        // right operand resolves to the code under test's own constant,
        // not a plain literal - legitimate contract pin
        assertEquals(3.14159, MathLib.PI, 0.00001);
    }
}
