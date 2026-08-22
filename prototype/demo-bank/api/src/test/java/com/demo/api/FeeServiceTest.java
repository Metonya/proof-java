package com.demo.api;

import com.tinytest.Test;
import static com.tinytest.Assert.*;

public class FeeServiceTest {

    private final FeeService service = new FeeService();

    // GOOD
    @Test
    public void domesticFeeForRegularTier() {
        assertEquals(1.0, service.transferFee(200.0, "BASIC", false), 0.001);
    }

    // GOOD: distinct branch (international surcharge + free shipping halving)
    @Test
    public void internationalFeeForGoldTier() {
        assertEquals(15.0, service.transferFee(1000.0, "GOLD", true), 0.001);
    }

    // GOOD
    @Test
    public void feeCategoryMedium() {
        assertEquals("MEDIUM", service.feeCategory(20.0));
    }

    // SMELL: no assertion
    @Test
    public void testFeeCategoryLow() {
        service.feeCategory(5.0);
    }
}
