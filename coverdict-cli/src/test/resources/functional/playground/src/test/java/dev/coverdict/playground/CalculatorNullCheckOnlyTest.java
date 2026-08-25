package dev.coverdict.playground;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * expect: finding rule=NULL_CHECK_ONLY method=describeOnlyChecksNonNull confidence=HIGH
 * describe(7) covers Calculator.isPositive as a side effect; with
 * --mutation-report its single surviving mutant is unobserved by any test
 * (this one only checks non-nullness), so it also independently satisfies
 * PSEUDO_TESTED_METHOD - a real, verified emergent finding, not designed here.
 * expect (with --mutation-report): finding rule=PSEUDO_TESTED_METHOD productionMethod=Calculator#isPositive(I)Z confidence=HIGH
 */
class CalculatorNullCheckOnlyTest {

    private final Calculator calculator = new Calculator();

    @Test
    void describeOnlyChecksNonNull() {
        String result = calculator.describe(7);
        assertNotNull(result); // content ("positive"/"negative"/"zero") never verified
    }
}
