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

    private PitJdkSupport() {
    }
}
