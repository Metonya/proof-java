package rules.no_recognized_oracle;

import org.junit.jupiter.api.Test;

// Deliberately not compilable: AssertLib and DataHelper are absent from the
// fixture classpath, simulating an unresolved custom helper (D-17).
// expect: finding method=unresolvedOracleSuggestiveCall confidence=INCONCLUSIVE
// expect: finding method=unresolvedNeutralCall confidence=MEDIUM
class Unresolved {

    static class Calc {
        int add(int a, int b) { return a + b; }
    }

    @Test
    void unresolvedOracleSuggestiveCall() {
        int result = new Calc().add(2, 3);
        AssertLib.assertValid(result); // unresolved, matches assert* pattern
    }

    @Test
    void unresolvedNeutralCall() {
        int result = new Calc().add(2, 3);
        DataHelper.record(result); // unresolved, not oracle-suggestive
    }
}
