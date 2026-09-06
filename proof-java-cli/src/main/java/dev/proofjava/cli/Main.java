package dev.proofjava.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Entry point. Every surface consumes the CLI's output (D-01). */
@Command(
    name = "proof-java",
    versionProvider = VersionProvider.class,
    subcommands = { AnalyzeCommand.class, DoctorCommand.class, RenderHtmlCommand.class },
    description = "Deterministic verdict layer for Java test suites."
)
public class Main implements Runnable {

    // Not mixinStandardHelpOptions: that mixin only accepts -V for version
    // (picocli avoids -v because plenty of CLIs use it for --verbose
    // instead), which is a real, reported point of confusion for a tool
    // with no --verbose to collide with. -v is added here as a plain
    // alias, -V is kept for anyone used to the picocli/GNU convention.
    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = { "-v", "-V", "--version" }, versionHelp = true, description = "Print version information and exit.")
    private boolean versionRequested;

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
