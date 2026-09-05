package dev.proofjava.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestLabelsTest {

    @Test
    void readsAPlainClassHashMethodId() {
        assertEquals("CalculatorTest#add", TestLabels.readable("com.example.CalculatorTest#add()"));
    }

    @Test
    void readsAJunit5UniqueId() {
        assertEquals("FooTest#bar",
            TestLabels.readable("[engine:junit-jupiter]/[class:com.demo.FooTest]/[method:bar()]"));
    }

    @Test
    void readsAJunit4VintageId() {
        assertEquals("FooTest#bar",
            TestLabels.readable("[engine:junit-vintage]/[runner:com.demo.FooTest]/[test:bar(com.demo.FooTest)]"));
    }

    /**
     * The id that made a gson report unreadable: a Guava-testlib suite
     * descriptor, percent-encoded, ~250 characters, dozens per mutant. %5B is
     * '[' and %3A is ':'.
     */
    @Test
    void collapsesTheGeneratedSuiteDescriptorThatMadeGsonUnreadable() {
        String raw = "com.google.gson.JsonArrayAsListSuiteTest.[engine:junit-vintage]"
            + "/[runner:com.google.gson.JsonArrayAsListSuiteTest]"
            + "/[test:JsonArray#asList %5Bcollection size%3A several%5D]"
            + "/[test:com.google.common.collect.testing.testers.CollectionContainsAllTester]";

        String label = TestLabels.readable(raw);

        assertTrue(label.startsWith("JsonArrayAsListSuiteTest#"), label);
        assertTrue(label.length() < 60, "a table cell has to fit: " + label);
        assertTrue(!label.contains("%5B"), "percent escapes are decoded, not shown: " + label);
    }

    @Test
    void shortensTheMutatorClassName() {
        assertEquals("BooleanFalseReturnVals", TestLabels.shortMutator(
            "org.pitest.mutationtest.engine.gregor.mutators.returns.BooleanFalseReturnValsMutator"));
    }

    @Test
    void leavesSomethingItCannotParseUsableRatherThanEmpty() {
        assertEquals("", TestLabels.readable(null));
        assertEquals("", TestLabels.readable("  "));
        assertTrue(TestLabels.readable("something unstructured").length() > 0);
    }
}
