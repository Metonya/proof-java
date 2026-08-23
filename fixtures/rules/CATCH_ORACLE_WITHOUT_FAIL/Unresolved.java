package rules.catch_oracle_without_fail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Deliberately not compilable: TestSupport is absent from the fixture
// classpath; failLoudly matches the oracle-suggestive fail* pattern, so the
// catch block may contain a custom fail helper (D-17).
// expect: finding method=unresolvedCustomFailInCatch confidence=INCONCLUSIVE
class Unresolved {

    static class Parser {
        int parse(String s) { return Integer.parseInt(s); }
    }

    @Test
    void unresolvedCustomFailInCatch() {
        try {
            int v = new Parser().parse("44");
            assertEquals(44, v);
        } catch (Exception e) {
            TestSupport.failLoudly(e); // unresolved, matches fail* pattern
        }
    }
}
