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
}
