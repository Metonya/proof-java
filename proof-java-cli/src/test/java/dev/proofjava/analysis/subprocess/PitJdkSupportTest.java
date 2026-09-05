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

    /**
     * D-95: the same ASM ceiling can crash PIT on a class file that has
     * nothing to do with the running JDK - any classpath entry compiled past
     * it. Found on junit-framework: its own junit-jupiter-api test-fixtures
     * jar was JDK 25 bytecode, and PIT's KotlinVerifier pre-flight step
     * scanned it before ever mutating the (ordinary Java 17) target class.
     */
    @Test
    void aClasspathBytecodeCrashIsRecognizedByItsSignature() {
        String realFailure = "Module 'demo' mutation subprocess exited 1 (0/1 class(es) completed) (output tail:\n"
            + "Exception in thread \"main\" java.lang.IllegalArgumentException: Unsupported class file major version 69\n"
            + "\tat org.pitest.mutationtest.verify.KotlinVerifier.kotlinClassesToBeMutated(KotlinVerifierFactory.java:42))";

        assertTrue(PitJdkSupport.isClasspathBytecodeCrash(realFailure), realFailure);
    }

    @Test
    void anUnrelatedFailureIsNotMisclassifiedAsABytecodeCrash() {
        assertFalse(PitJdkSupport.isClasspathBytecodeCrash("Module 'demo' mutation subprocess exited 1"));
    }

    @Test
    void aNullMessageIsNotABytecodeCrash() {
        assertFalse(PitJdkSupport.isClasspathBytecodeCrash(null));
    }

    @Test
    void theHintNamesTheCeilingSoAReaderKnowsWhatToLookFor() {
        assertTrue(PitJdkSupport.classpathBytecodeHint().contains(String.valueOf(PitJdkSupport.MAX_SUPPORTED_JDK)));
    }
}
