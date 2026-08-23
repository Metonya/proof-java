package dev.coverdict.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The only v0.1 subcommand. Option binding and the analysis itself arrive with
 * M1a; until then the command exists so the exit-code contract and the
 * fail-closed posture are wired from the first commit.
 */
@Command(name = "analyze", description = "Analyze coverage and test-oracle evidence for a repository.")
class AnalyzeCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().getErr().println(
            "coverdict: analysis is not implemented yet (M1a). "
            + "No verdict was produced, so this run is reported as incomplete "
            + "rather than as a passing analysis.");
        return ExitCode.INCOMPLETE.value();
    }
}
