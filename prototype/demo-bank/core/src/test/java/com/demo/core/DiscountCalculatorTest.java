package com.demo.core;

import com.tinytest.Test;
import static com.tinytest.Assert.*;

public class DiscountCalculatorTest {

    private final DiscountCalculator calc = new DiscountCalculator();

    // GOOD: real behaviour is pinned down
    @Test
    public void calculatesGoldDiscount() {
        double result = calc.calculate(1000.0, "GOLD");
        assertEquals(800.0, result, 0.001);
    }

    // SMELL: duplicate of calculatesGoldDiscount, identical execution + identical oracle
    @Test
    public void testCalculateForGoldCustomer() {
        double result = calc.calculate(1000.0, "GOLD");
        assertEquals(800.0, result, 0.001);
    }

    // SMELL: no assertion at all, pure coverage inflation
    @Test
    public void testCalculateRuns() {
        calc.calculate(2000.0, "SILVER");
    }

    // SMELL: tautological assertion, passes for almost any implementation
    @Test
    public void testCalculateReturnsSomething() {
        double result = calc.calculate(2000.0, "SILVER");
        assertTrue(result >= 0);
    }

    // GOOD: covers a distinct branch with a real oracle
    @Test
    public void rateForSilverIsTenPercent() {
        assertEquals(0.10, calc.rateFor("SILVER"), 0.0001);
    }

    // SMELL: exception swallowed, assertion unreachable on the interesting path
    @Test
    public void shouldNotThrowForStaffTier() {
        try {
            calc.calculate(500.0, "STAFF");
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    // new code: only one branch touched, oracle is weak
    @Test
    public void testApplyLoyaltyBonus() {
        double result = calc.applyLoyaltyBonus(1000.0, 6);
        assertTrue(result > 0);
    }
}
