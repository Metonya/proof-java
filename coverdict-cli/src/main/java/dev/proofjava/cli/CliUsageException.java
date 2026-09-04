package dev.proofjava.cli;

/**
 * A CLI invocation problem (bad option syntax, conflicting options) as
 * opposed to a problem with the evidence itself. Maps to exit 2, and - per
 * the schema's own note ("Exit codes 2 and 4 abort before a verdict document
 * is written") - never produces a verdict JSON, unlike
 * {@link dev.proofjava.analysis.AnalysisException} (exit 3, always produces
 * a structured incomplete document).
 */
class CliUsageException extends RuntimeException {

    CliUsageException(String message) {
        super(message);
    }
}
