package rules.no_recognized_oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.truth.Truth;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

// Deliberately unresolvable: not on the fixture classpath, simulating an
// external functional-interface parameter type with no jar configured (D-33).
import some.external.ThrowingRunnable;

// expect: none method=junit5Assertion
// expect: none method=junit4Assertion
// expect: none method=assertjChain
// expect: none method=mockitoVerify
// expect: none method=expectedException
// expect: none method=oracleInPrivateHelper
// expect: none method=truthChain
// expect: none method=truthChainInPrivateHelper
// expect: none method=oracleInPublicStaticHelper
// expect: none method=oracleReachedThroughUnresolvableParamTypeHelper
// expect: none method=bddAssertionsThenChain
// expect: none method=assertThatExceptionFamilyChain
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

    @Test
    void truthChain() {
        Truth.assertThat(new Calc().add(2, 2)).isEqualTo(4);
    }

    @Test
    void truthChainInPrivateHelper() {
        roundTrip(new Calc(), 3, 3, 6);
    }

    private void roundTrip(Calc calc, int a, int b, int expected) {
        Truth.assertWithMessage("sum").that(calc.add(a, b)).isEqualTo(expected);
    }

    @Test
    void oracleInPublicStaticHelper() {
        checkSumPublicStatic(new Calc(), 4, 4, 8);
    }

    // Same-file, but NOT private (D-33) - traversal must still follow it.
    static void checkSumPublicStatic(Calc calc, int a, int b, int expected) {
        assertEquals(expected, calc.add(a, b));
    }

    @Test
    void oracleReachedThroughUnresolvableParamTypeHelper() {
        assertThrowsSomething(() -> new Calc().div(1, 0));
    }

    // Symbol Solver cannot resolve THIS declaration either (its own parameter
    // type is unresolvable), so the call site's resolution fails the same way
    // regardless of the argument being a lambda (D-33) - the same-file
    // name-match fallback must still find this single, unambiguous match.
    private static void assertThrowsSomething(ThrowingRunnable runnable) {
        assertThrows(ArithmeticException.class, runnable);
    }

    @Test
    void bddAssertionsThenChain() {
        BDDAssertions.then(new Calc().add(2, 2)).isEqualTo(4);
    }

    @Test
    void assertThatExceptionFamilyChain() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
            .isThrownBy(() -> {
                throw new NullPointerException();
            });
    }
}
