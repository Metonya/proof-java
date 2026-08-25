package dev.coverdict.playground;

import org.junit.jupiter.api.Test;

/**
 * expect: finding rule=CATCH_ORACLE_WITHOUT_FAIL method=divideByZeroSwallowed confidence=HIGH
 */
class CalculatorCatchWithoutFailTest {

    private final Calculator calculator = new Calculator();

    @Test
    void divideByZeroSwallowed() {
        try {
            calculator.divide(1, 0);
        } catch (ArithmeticException e) {
            // swallowed: no fail(), no rethrow, no oracle - the test passes either way
        }
    }
}
