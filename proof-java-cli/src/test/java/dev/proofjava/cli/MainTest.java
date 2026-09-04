package dev.proofjava.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class MainTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute(args);
    }

    @Test
    void versionReportsToolAndSchemaVersion() {
        assertEquals(ExitCode.COMPLETE.value(), run("--version"));
        String output = out.toString();
        assertTrue(output.contains("proof-java 0.1.0"), output);
        assertTrue(output.contains("verdict schema 0.1.0"), output);
    }

    @Test
    void unknownOptionIsInvalidInput() {
        assertEquals(ExitCode.INVALID_INPUT.value(), run("--not-an-option"));
    }

    @Test
    void analyzeWithoutADiffModeIsInvalidInvocation() {
        // M0-CLI-INPUT.md: exactly one diff mode is required; none is exit 2,
        // not a passing/incomplete analysis. Deep behavior of --no-vcs itself
        // (parsing, binding, the incomplete-verdict contract) lives in
        // AnalyzeCommandTest - this only proves Main dispatches into it.
        assertEquals(ExitCode.INVALID_INPUT.value(), run("analyze"));
    }

    @Test
    void noArgsRunsWithoutErrorAndPrintsNothing() {
        // Exercises Main.run() itself (the no-subcommand path): it is a no-op,
        // not a usage screen - that requires an explicit --help. main()/System.exit
        // is intentionally not unit-tested here - see class-level exclusion in pom.xml.
        assertEquals(ExitCode.COMPLETE.value(), run());
        assertEquals("", out.toString());
        assertEquals("", err.toString());
    }

    @Test
    void executionExceptionMapsToInternalErrorExitCode() throws Exception {
        // No wired command throws today, so the handler is exercised directly:
        // this is the real exit-4 contract path (hard rule 3a: a run that
        // crashed must never exit 0), not a hypothetical.
        CommandLine cmd = Main.commandLine();
        cmd.setErr(new PrintWriter(err));
        int exitCode = cmd.getExecutionExceptionHandler()
            .handleExecutionException(new RuntimeException("boom"), cmd, null);
        assertEquals(ExitCode.INTERNAL_ERROR.value(), exitCode);
        assertTrue(err.toString().contains("boom"), err.toString());
    }

    @Test
    void exitCodeOneIsReservedAndUnused() {
        // M3 reserves 1 for the quality gate; v0.1 must never emit it.
        for (ExitCode code : ExitCode.values()) {
            assertNotEquals(1, code.value(), "v0.1 must not define exit code 1: " + code);
        }
    }
}
