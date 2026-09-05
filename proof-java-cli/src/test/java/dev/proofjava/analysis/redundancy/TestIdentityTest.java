package dev.proofjava.analysis.redundancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TestIdentityTest {

    @Test
    void parsesTheHashSeparatedShapeWithParens() {
        TestIdentity.Parsed parsed = TestIdentity.parse("com.demo.core.IbanValidatorTest#acceptsAValidGermanIban()");

        assertEquals("com.demo.core.IbanValidatorTest", parsed.className());
        assertEquals("acceptsAValidGermanIban", parsed.methodName());
    }

    @Test
    void parsesTheHashSeparatedShapeWithoutParens() {
        TestIdentity.Parsed parsed = TestIdentity.parse("com.example.CalcTest#addsTwoNumbers");

        assertEquals("com.example.CalcTest", parsed.className());
        assertEquals("addsTwoNumbers", parsed.methodName());
    }

    @Test
    void parsesAJUnit5UniqueIdShape() {
        TestIdentity.Parsed parsed = TestIdentity.parse(
            "[engine:junit-jupiter]/[class:com.demo.FooTest]/[method:bar()]");

        assertEquals("com.demo.FooTest", parsed.className());
        assertEquals("bar", parsed.methodName());
    }

    @Test
    void returnsNullForAnUnrecognizedShape() {
        assertNull(TestIdentity.parse("not-a-recognized-test-id"));
    }

    @Test
    void returnsNullWhenTheHashHasNothingAfterIt() {
        assertNull(TestIdentity.parse("com.example.CalcTest#"));
    }

    @Test
    void parsesAJunit4VintageIdShape() {
        TestIdentity.Parsed parsed = TestIdentity.parse(
            "[engine:junit-vintage]/[runner:com.demo.FooTest]/[test:bar(com.demo.FooTest)]");

        assertEquals("com.demo.FooTest", parsed.className());
        assertEquals("bar", parsed.methodName());
    }

    /**
     * The exact id that aborted a whole gson analysis. A generated suite
     * descriptor contains a '#', so the bare-hash fallback treated everything
     * before it - brackets, colons and all - as a class name, which TestLocator
     * turned into a file path. Windows rejects ':' in a path, so the run died
     * with InvalidPathException instead of simply not enriching one finding.
     * Nothing here names a locatable method, so the answer is "unresolved".
     */
    @Test
    void aGeneratedSuiteDescriptorIsUnresolvedRatherThanMisparsedAsAClassName() {
        String realGsonId = "com.google.gson.JsonArrayAsListSuiteTest.[engine:junit-vintage]"
            + "/[runner:com.google.gson.JsonArrayAsListSuiteTest]"
            + "/[test:JsonArray#asList %5Bcollection size%3A zero%5D]"
            + "/[test:com.google.common.collect.testing.testers.CollectionAddTester]";

        assertNull(TestIdentity.parse(realGsonId));
    }

    @Test
    void aClassNameThatCouldNotBeOneIsRejected() {
        assertNull(TestIdentity.parse("[weird:thing]/not a class#method"));
    }
}
