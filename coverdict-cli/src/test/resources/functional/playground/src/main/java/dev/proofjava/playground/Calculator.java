package dev.proofjava.playground;

/** Small, deliberately branchy production class used as coverdict's test fixture. */
public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("division by zero");
        }
        return a / b;
    }

    public boolean isPositive(int x) {
        return x > 0;
    }

    public String describe(int x) {
        if (x == 0) {
            return "zero";
        }
        return isPositive(x) ? "positive" : "negative";
    }

    public int square(int x) {
        return x * x;
    }
}
