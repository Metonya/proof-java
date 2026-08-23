package rules.null_check_only;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

// Deliberately not compilable: Audit and AssertSupport are absent from the
// fixture classpath.
// expect: finding method=nullCheckPlusUnresolvedNeutral confidence=MEDIUM
// expect: none method=nullCheckPlusUnresolvedOracleSuggestive
class Unresolved {

    static class Repo {
        String find(int id) { return "row-" + id; }
    }

    @Test
    void nullCheckPlusUnresolvedNeutral() {
        String row = new Repo().find(1);
        assertNotNull(row);
        Audit.record(row); // unresolved, not oracle-suggestive
    }

    @Test
    void nullCheckPlusUnresolvedOracleSuggestive() {
        String row = new Repo().find(2);
        assertNotNull(row);
        // unresolved and assert-suggestive: the "only null checks" premise is
        // unprovable, rule stays silent per spec
        AssertSupport.assertRowShape(row);
    }
}
