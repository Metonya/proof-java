package dev.coverdict.playground;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * expect: finding rule=NO_RECOGNIZED_ORACLE method=addCheckedViaLocalSoftAssertions confidence=INCONCLUSIVE
 * SoftAssertions is a real AssertJ dependency, but run.ps1 never passes
 * --classpath to coverdict, so its jar is invisible to coverdict's oracle
 * resolver. Held in a local variable (not a field, not a static import), none
 * of OracleRecognizer's fallback anchoring paths apply either, so
 * softly.assertThat(...)/.assertAll() are genuinely unresolved calls - and
 * "assert..." matches the oracle-suggestive name pattern, exercising
 * NO_RECOGNIZED_ORACLE's INCONCLUSIVE path (a test that may well have a real
 * oracle, but coverdict cannot confirm it without --classpath).
 *
 * <p>Deliberately calls {@code add}, not another method: {@code add}'s sole
 * mutant is already killed by {@code CalculatorGoodTest#addWorksCorrectly}, so
 * this test's equal (not superset) kill-set never trips SUBSUMED_TEST as a
 * side effect - unlike an earlier draft that called {@code multiply} and
 * incidentally produced an extra, unrelated SUBSUMED_TEST finding.
 */
class CalculatorUnresolvedOracleTest {

    private final Calculator calculator = new Calculator();

    @Test
    void addCheckedViaLocalSoftAssertions() {
        int result = calculator.add(2, 3);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result).isEqualTo(5);
        softly.assertAll();
    }
}
