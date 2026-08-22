package com.tinytest;

public class Assert {
    public static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("expected true");
        }
    }

    public static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("expected false");
        }
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            throw new AssertionError("expected non-null");
        }
    }
}
