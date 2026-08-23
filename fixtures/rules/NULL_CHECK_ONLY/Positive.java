package rules.null_check_only;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

// expect: finding method=onlyAssertNotNull confidence=HIGH
// expect: finding method=onlyAssertjIsNotNull confidence=HIGH
class Positive {

    static class Repo {
        String find(int id) { return "row-" + id; }
    }

    @Test
    void onlyAssertNotNull() {
        String row = new Repo().find(1);
        assertNotNull(row);
    }

    @Test
    void onlyAssertjIsNotNull() {
        assertThat(new Repo().find(2)).isNotNull();
    }
}
