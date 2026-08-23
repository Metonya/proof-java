package rules.no_recognized_oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

// expect: none method=junit5Assertion
// expect: none method=junit4Assertion
// expect: none method=assertjChain
// expect: none method=mockitoVerify
// expect: none method=expectedException
// expect: none method=oracleInPrivateHelper
class Negative {

    static class Calc {
        int add(int a, int b) { return a + b; }
        int div(int a, int b) { return a / b; }
    }

    interface Listener { void onDone(int v); }

    @Test
    void junit5Assertion() {
        assertEquals(5, new Calc().add(2, 3));
    }

    @org.junit.Test
    public void junit4Assertion() {
        org.junit.Assert.assertEquals(4, new Calc().add(2, 2));
    }

    @Test
    void assertjChain() {
        assertThat(new Calc().add(1, 1)).isEqualTo(2);
    }

    @Test
    void mockitoVerify() {
        Listener listener = mock(Listener.class);
        listener.onDone(new Calc().add(3, 3));
        verify(listener).onDone(6);
    }

    @Test
    void expectedException() {
        assertThrows(ArithmeticException.class, () -> new Calc().div(1, 0));
    }

    @Test
    void oracleInPrivateHelper() {
        checkSum(new Calc(), 2, 3, 5);
    }

    private void checkSum(Calc calc, int a, int b, int expected) {
        assertEquals(expected, calc.add(a, b));
    }
}
