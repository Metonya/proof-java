package com.demo.api;

import com.demo.core.DiscountCalculator;

public class FeeService {

    private final DiscountCalculator calculator = new DiscountCalculator();

    public double transferFee(double amount, String tier, boolean international) {
        double base = amount * 0.005;
        if (international) {
            base = base + 25.0;
        }
        if (calculator.isEligibleForFreeShipping(amount, tier)) {
            base = base / 2.0;
        }
        if (base > 100.0) {
            return 100.0;
        }
        return base;
    }

    public String feeCategory(double fee) {
        if (fee == 0.0) {
            return "FREE";
        }
        if (fee < 10.0) {
            return "LOW";
        }
        if (fee < 50.0) {
            return "MEDIUM";
        }
        return "HIGH";
    }
}
