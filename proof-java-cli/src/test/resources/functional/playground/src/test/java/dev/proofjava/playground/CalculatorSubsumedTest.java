package dev.proofjava.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * divideNarrow only asserts on Calculator.divide's normal-path return;
 * divideAndMultiplyWide asserts on divide AND multiply, killing a strict
 * superset of the mutants divideNarrow kills. With --mutation-report,
 * divideNarrow's kill-set is a strict subset of the wide test's.
 * expect (with --mutation-report): finding rule=SUBSUMED_TEST testMethod=divideNarrow relatedTestMethod=divideAndMultiplyWide confidence=MEDIUM (subsumed kill count below 3)
 */
class CalculatorSubsumedTest {

    private final Calculator calculator = new Calculator();

    @Test
    void divideNarrow() {
        assertEquals(5, calculator.divide(10, 2));
    }

    @Test
    void divideAndMultiplyWide() {
        assertEquals(5, calculator.divide(10, 2));
        assertEquals(6, calculator.multiply(2, 3));
    }
}
