package dev.proofjava.cli;

/**
 * The v0.1 exit-code contract (ROADMAP M1). {@code 1} is deliberately absent:
 * it is reserved for the M3 finding-based quality gate and never emitted by
 * v0.1, so a caller can distinguish "findings exist" from "run failed" once
 * that gate ships.
 */
public enum ExitCode {

    /** Analysis completed, whether or not it produced findings. */
    COMPLETE(0),

    /** Invalid invocation or unusable input. */
    INVALID_INPUT(2),

    /** Evidence missing, ambiguous, or unverified: never a false green. */
    INCOMPLETE(3),

    /** Internal failure. */
    INTERNAL_ERROR(4);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
