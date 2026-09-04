package dev.proofjava.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * True negative: well-formed tests with real oracles on real behavior.
 * expect: no findings from any proof-java rule for this file.
 */
class CalculatorGoodTest {

    private final Calculator calculator = new Calculator();

    @Test
    void addWorksCorrectly() {
        assertEquals(5, calculator.add(2, 3));
    }

    @Test
    void divideThrowsOnZero() {
        assertThrows(ArithmeticException.class, () -> calculator.divide(1, 0));
    }
}
