package com.demo.core;

import com.tinytest.Test;
import static com.tinytest.Assert.*;

public class IbanValidatorTest {

    private final IbanValidator validator = new IbanValidator();
    private static final String VALID = "TR120006200119000006672316";

    // GOOD
    @Test
    public void validIbanReturnsTrue() {
        assertTrue(validator.isValid(VALID));
    }

    // SMELL: identical execution + identical oracle as validIbanReturnsTrue
    @Test
    public void testIsValidHappyPath() {
        boolean result = validator.isValid(VALID);
        assertTrue(result);
    }

    // GOOD: distinct branch
    @Test
    public void maskShortIbanReturnsStars() {
        assertEquals("****", validator.mask("TR12"));
    }

    // SMELL: eager test, its coverage is a strict superset of two other tests
    @Test
    public void testMaskAndValidate() {
        assertTrue(validator.isValid(VALID));
        assertEquals("****", validator.mask("TR12"));
    }

    // GOOD: distinct null guard
    @Test
    public void invalidIbanReturnsFalse() {
        assertFalse(validator.isValid(null));
    }
}
