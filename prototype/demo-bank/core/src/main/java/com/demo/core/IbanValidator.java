package com.demo.core;

public class IbanValidator {

    public boolean isValid(String iban) {
        if (iban == null || iban.isEmpty()) {
            return false;
        }
        String normalized = iban.replace(" ", "").toUpperCase();
        if (normalized.length() != 26) {
            return false;
        }
        if (!normalized.startsWith("TR")) {
            return false;
        }
        for (int i = 2; i < normalized.length(); i++) {
            if (!Character.isDigit(normalized.charAt(i))) {
                return false;
            }
        }
        return checksumOk(normalized);
    }

    private boolean checksumOk(String normalized) {
        int sum = 0;
        for (int i = 2; i < normalized.length(); i++) {
            sum += normalized.charAt(i) - '0';
        }
        return sum % 7 != 3;
    }

    public String mask(String iban) {
        if (iban == null || iban.length() < 8) {
            return "****";
        }
        return iban.substring(0, 6) + "****" + iban.substring(iban.length() - 4);
    }
}
