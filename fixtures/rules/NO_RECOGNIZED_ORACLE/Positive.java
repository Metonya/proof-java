package rules.no_recognized_oracle;

import org.junit.jupiter.api.Test;

// expect: finding method=printsResultOnly confidence=HIGH
// expect: finding method=helperWithoutOracle confidence=HIGH
// expect: finding method=junit4NoOracle confidence=HIGH
class Positive {

    static class Calc {
        int add(int a, int b) { return a + b; }
    }

    @Test
    void printsResultOnly() {
        Calc calc = new Calc();
        int result = calc.add(2, 3);
        System.out.println("result=" + result);
    }

    @Test
    void helperWithoutOracle() {
        run(new Calc());
    }

    private void run(Calc calc) {
        calc.add(1, 1);
        // resolved private helper, still no oracle anywhere in the unit
    }

    @org.junit.Test
    public void junit4NoOracle() {
        new Calc().add(4, 5);
    }
}
