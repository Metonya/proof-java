package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PitJdkSupportTest {

    @Test
    void theCeilingIsInclusiveAndTheVersionAboveItIsRefused() {
        assertTrue(PitJdkSupport.isSupported(17));
        assertTrue(PitJdkSupport.isSupported(PitJdkSupport.MAX_SUPPORTED_JDK));
        assertFalse(PitJdkSupport.isSupported(PitJdkSupport.MAX_SUPPORTED_JDK + 1));
        assertFalse(PitJdkSupport.isSupported(25));
    }

    /**
     * The message has to name the refused option and both versions: a user
     * reading it in the report needs to know what was skipped and what to
     * re-run it on, not merely that something was unsupported.
     */
    @Test
    void theMessageNamesTheOptionTheCeilingAndTheRunningJdk() {
        String message = PitJdkSupport.unsupportedMessage("--mutation-report");

        assertTrue(message.contains("--mutation-report"), message);
        assertTrue(message.contains(String.valueOf(PitJdkSupport.MAX_SUPPORTED_JDK)), message);
        assertTrue(message.contains(String.valueOf(Runtime.version().feature())), message);
    }

    /**
     * Guards the constant itself: PIT 1.15.8's bundled ASM knows class files up
     * to Java 22, so raising this without upgrading PIT would re-open the silent
     * false green it exists to prevent.
     */
    @Test
    void theCeilingMatchesTheEmbeddedEngine() {
        assertEquals(22, PitJdkSupport.MAX_SUPPORTED_JDK);
    }
}
