package dev.proofjava.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point. Every surface consumes the CLI's output (D-01). */
@Command(
    name = "proof-java",
    versionProvider = VersionProvider.class,
    mixinStandardHelpOptions = true,
    subcommands = { AnalyzeCommand.class, DoctorCommand.class, RenderHtmlCommand.class },
    description = "Deterministic verdict layer for Java test suites."
)
public class Main implements Runnable {

    @Override
    public void run() {
        // No subcommand given: picocli prints usage via the usage handler below.
    }

    static CommandLine commandLine() {
        return new CommandLine(new Main())
            .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                cmd.getErr().println("proof-java: internal error: " + ex);
                return ExitCode.INTERNAL_ERROR.value();
            })
            .setParameterExceptionHandler((ex, args) -> {
                CommandLine cmd = ex.getCommandLine();
                cmd.getErr().println("proof-java: " + ex.getMessage());
                cmd.usage(cmd.getErr());
                return ExitCode.INVALID_INPUT.value();
            });
    }

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }
}
