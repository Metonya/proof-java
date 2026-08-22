package com.demo.core;

public class DiscountCalculator {

    public double calculate(double amount, String customerTier) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        double rate = rateFor(customerTier);
        double discounted = amount - (amount * rate);
        if (discounted < 10.0) {
            return 10.0;
        }
        return round2(discounted);
    }

    public double rateFor(String customerTier) {
        if ("GOLD".equals(customerTier)) {
            return 0.20;
        }
        if ("SILVER".equals(customerTier)) {
            return 0.10;
        }
        if ("STAFF".equals(customerTier)) {
            return 0.50;
        }
        return 0.0;
    }

    public boolean isEligibleForFreeShipping(double amount, String customerTier) {
        if ("GOLD".equals(customerTier)) {
            return true;
        }
        return amount >= 500.0;
    }

    public double applyLoyaltyBonus(double amount, int loyaltyYears) {
        if (loyaltyYears < 0) {
            throw new IllegalArgumentException("loyaltyYears must not be negative");
        }
        if (loyaltyYears >= 10) {
            return amount * 0.85;
        }
        if (loyaltyYears >= 5) {
            return amount * 0.92;
        }
        if (loyaltyYears >= 2) {
            return amount * 0.97;
        }
        return amount;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
