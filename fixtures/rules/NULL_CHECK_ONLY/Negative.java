package rules.null_check_only;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

// expect: none method=mixedOracles
// expect: none method=contentOnly
class Negative {

    static class Repo {
        String find(int id) { return "row-" + id; }
    }

    @Test
    void mixedOracles() {
        String row = new Repo().find(1);
        assertNotNull(row);
        assertEquals("row-1", row);
    }

    @Test
    void contentOnly() {
        assertEquals("row-2", new Repo().find(2));
    }
}
