package dev.proofjava.playground;

import org.junit.jupiter.api.Test;

/**
 * expect: finding rule=NO_RECOGNIZED_ORACLE method=subtractHasNoAssertion confidence=HIGH
 * Also the sole test covering Calculator.subtract, so with --mutation-report
 * it should additionally surface as PSEUDO_TESTED_METHOD on subtract (every
 * generated mutant survives - nothing here observes the return value).
 */
class CalculatorNoOracleTest {

    private final Calculator calculator = new Calculator();

    @Test
    void subtractHasNoAssertion() {
        calculator.subtract(5, 3);
    }
}
