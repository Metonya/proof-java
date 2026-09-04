package dev.proofjava.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * expect: no findings - a @ParameterizedTest with a real assertEquals oracle
 * per invocation. AGENTS.md hard rule 2a: @ParameterizedTest is a normal,
 * everyday test shape, not an edge case - this confirms coverdict's static
 * L0 traversal handles the annotation correctly rather than miscounting the
 * method as untested or producing a false NO_RECOGNIZED_ORACLE positive.
 */
class CalculatorParameterizedTest {

    private final Calculator calculator = new Calculator();

    @ParameterizedTest
    @CsvSource({"2,3,5", "10,-4,6", "0,0,0"})
    void addProducesTheSumForEveryPair(int a, int b, int expected) {
        assertEquals(expected, calculator.add(a, b));
    }
}
