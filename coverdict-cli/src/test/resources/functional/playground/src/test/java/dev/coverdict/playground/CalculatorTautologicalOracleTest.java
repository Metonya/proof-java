package dev.coverdict.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * expect: finding rule=TAUTOLOGICAL_ORACLE method=multiplyConstantVsConstant confidence=HIGH
 * expect: finding rule=TAUTOLOGICAL_ORACLE method=multiplyLiteralBoolean confidence=HIGH
 */
class CalculatorTautologicalOracleTest {

    private final Calculator calculator = new Calculator();

    @Test
    void multiplyConstantVsConstant() {
        calculator.multiply(2, 2);
        assertEquals(4, 2 * 2); // constant vs constant, never depends on multiply's result
    }

    @Test
    void multiplyLiteralBoolean() {
        calculator.multiply(3, 3);
        assertTrue(true); // literal boolean, never depends on multiply's result
    }
}
