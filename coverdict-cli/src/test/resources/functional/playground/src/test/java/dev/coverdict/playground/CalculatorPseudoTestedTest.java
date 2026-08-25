package dev.coverdict.playground;

import org.junit.jupiter.api.Test;

/**
 * Calculator.square is covered by exactly this one test, which never asserts
 * on the return value - with --mutation-report every gregor RETURNS mutant
 * generated for square should survive.
 * expect (with --mutation-report): finding rule=PSEUDO_TESTED_METHOD productionMethod=Calculator#square(I)I confidence=HIGH
 * expect (L0, always on): finding rule=NO_RECOGNIZED_ORACLE method=squareHasNoAssertion confidence=HIGH
 */
class CalculatorPseudoTestedTest {

    private final Calculator calculator = new Calculator();

    @Test
    void squareHasNoAssertion() {
        calculator.square(6);
    }
}
