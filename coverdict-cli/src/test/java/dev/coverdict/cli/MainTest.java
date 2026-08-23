package dev.coverdict.cli;

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
        assertTrue(output.contains("coverdict 0.1.0"), output);
        assertTrue(output.contains("verdict schema 0.1.0"), output);
    }

    @Test
    void unknownOptionIsInvalidInput() {
        assertEquals(ExitCode.INVALID_INPUT.value(), run("--not-an-option"));
    }

    @Test
    void unimplementedAnalysisIsIncompleteNotSuccess() {
        // Hard rule 3a: absent evidence must never exit 0.
        assertEquals(ExitCode.INCOMPLETE.value(), run("analyze"));
        assertTrue(err.toString().contains("incomplete"), err.toString());
    }

    @Test
    void exitCodeOneIsReservedAndUnused() {
        // M3 reserves 1 for the quality gate; v0.1 must never emit it.
        for (ExitCode code : ExitCode.values()) {
            assertNotEquals(1, code.value(), "v0.1 must not define exit code 1: " + code);
        }
    }
}
