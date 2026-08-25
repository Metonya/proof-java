package dev.coverdict.analysis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the exact fingerprint output for a known input, matching
 * docs/rules/README.md's definition byte-for-byte - guards against the kind
 * of silent corruption a plain-looking string literal caused once already in
 * this codebase (a NUL byte instead of a space, caught only by Sonar).
 */
class FingerprintTest {

    @Test
    void matchesTheDocumentedDefinitionForAKnownInput() {
        // sha256("NO_RECOGNIZED_ORACLE root src/test/java/com/example/CalcTest.java com.example.CalcTest#noAssertionHere()")
        String expected = "62207f0e943529e8";
        String actual = Fingerprint.compute("NO_RECOGNIZED_ORACLE", "root",
            "src/test/java/com/example/CalcTest.java", "com.example.CalcTest#noAssertionHere()");
        assertEquals(expected, actual);
    }

    @Test
    void isSixteenLowercaseHexCharacters() {
        String fp = Fingerprint.compute("NULL_CHECK_ONLY", "m", "p", "sig");
        assertEquals(16, fp.length());
        assertEquals(fp, fp.toLowerCase());
        assertEquals("", fp.replaceAll("[0-9a-f]", ""));
    }

    @Test
    void differsWhenAnyComponentDiffers() {
        String base = Fingerprint.compute("NULL_CHECK_ONLY", "root", "A.java", "A#m()");
        assertEquals(base, Fingerprint.compute("NULL_CHECK_ONLY", "root", "A.java", "A#m()"));
        org.junit.jupiter.api.Assertions.assertNotEquals(base, Fingerprint.compute("TAUTOLOGICAL_ORACLE", "root", "A.java", "A#m()"));
        org.junit.jupiter.api.Assertions.assertNotEquals(base, Fingerprint.compute("NULL_CHECK_ONLY", "other", "A.java", "A#m()"));
        org.junit.jupiter.api.Assertions.assertNotEquals(base, Fingerprint.compute("NULL_CHECK_ONLY", "root", "B.java", "A#m()"));
        org.junit.jupiter.api.Assertions.assertNotEquals(base, Fingerprint.compute("NULL_CHECK_ONLY", "root", "A.java", "A#n()"));
    }
}
