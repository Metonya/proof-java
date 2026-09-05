package dev.proofjava.analysis.subprocess;

/**
 * The JDK ceiling both PIT-driven evidence levels inherit.
 *
 * <p>PIT 1.15.8 bundles an ASM whose highest known class-file version is Java
 * 22. Run it on a newer JDK and its coverage minion throws
 * {@code Unsupported class file major version} while reading <em>the JDK's
 * own</em> classes - not the analysed project's. The damage is that this does
 * not surface as a failure: every mutant comes back {@code NO_COVERAGE}, so the
 * run reports zero findings, {@code status: complete}, exit 0, and no warning
 * naming the cause. Measured on this repo: 7 KILLED / 3 SURVIVED / 5 findings on
 * JDK 17, versus 12/12 {@code NO_COVERAGE} and 0 findings on JDK 25, same
 * command.
 *
 * <p>{@code MUTATION_EMPTY_EVIDENCE} does not catch it, because the evidence is
 * not empty - mutants were resolved and generated, they were just never
 * meaningfully executed. So L2/L3 refuse to run above the ceiling and report an
 * incomplete result instead, which is hard rule 3a applied to the analysis
 * environment rather than to the input.
 */
public final class PitJdkSupport {

    /** Highest JDK feature version the embedded PIT can read class files for. */
    public static final int MAX_SUPPORTED_JDK = 22;

    public static boolean isSupported(int jdkFeatureVersion) {
        return jdkFeatureVersion <= MAX_SUPPORTED_JDK;
    }

    public static boolean isRuntimeSupported() {
        return isSupported(Runtime.version().feature());
    }

    /** @param flag the option the user passed, so the message names what was refused. */
    public static String unsupportedMessage(String flag) {
        return flag + " needs a JDK of " + MAX_SUPPORTED_JDK + " or lower; this analysis is running on "
            + Runtime.version().feature() + ". The embedded mutation engine cannot read class files from a "
            + "newer JDK and would report zero findings without failing, so the evidence was not collected. "
            + "Re-run this analysis with a supported JDK.";
    }

    /**
     * The signature PIT's embedded ASM throws when it reads any class file
     * past its ceiling - not only when the running JDK itself is too new
     * ({@link #isRuntimeSupported}), but whenever such a class file sits
     * anywhere on the mutation/per-test classpath (D-95). Found on
     * junit-framework: its own {@code junit-jupiter-api} test-fixtures jar
     * was compiled to JDK 25 bytecode (class file major version 69), and
     * PIT's {@code KotlinVerifier} pre-flight step - which scans every
     * classpath entry for Kotlin metadata before mutating anything - crashed
     * the whole run on that one unrelated jar, even though the actual target
     * class was ordinary Java 17 bytecode that never got a chance to be
     * mutated.
     */
    private static final String CLASSPATH_BYTECODE_SIGNATURE = "Unsupported class file major version";

    /** @return true if a subprocess failure message names PIT's own class-file-version ceiling, wherever it hit it. */
    public static boolean isClasspathBytecodeCrash(String subprocessFailureMessage) {
        return subprocessFailureMessage != null && subprocessFailureMessage.contains(CLASSPATH_BYTECODE_SIGNATURE);
    }

    /** Appended to the raw subprocess message so the real cause is named, not just PIT's opaque exception. */
    public static String classpathBytecodeHint() {
        return " - a class file somewhere on this module's mutation/per-test classpath was compiled to a JDK "
            + "newer than " + MAX_SUPPORTED_JDK + " (a stale build artifact, or a dependency built with a newer "
            + "toolchain than the target class itself uses); the embedded mutation engine cannot read it, and "
            + "PIT aborts the whole run before mutating anything, regardless of the target class's own language level.";
    }

    private PitJdkSupport() {
    }
}
