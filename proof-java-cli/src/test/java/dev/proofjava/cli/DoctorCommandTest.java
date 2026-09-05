package dev.proofjava.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class DoctorCommandTest {

    @TempDir
    Path repoRoot;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute(args);
    }

    /**
     * D-96: before this, a Gradle-only repository (no pom.xml anywhere) got
     * the same bare "no Maven module found" message as a directory with no
     * build file of any kind - no hint that Gradle projects need `analyze`
     * wired by hand instead of through `doctor`. Found on junit-framework.
     */
    @Test
    void aGradleOnlyRepoGetsAHintInsteadOfABareMavenMessage() throws IOException {
        Files.writeString(repoRoot.resolve("build.gradle.kts"), "// a Gradle build\n");

        int exitCode = run("doctor", "--repo", repoRoot.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(err.toString().contains("no Maven module found"), err.toString());
        assertTrue(err.toString().contains("Gradle"), err.toString());
        assertTrue(err.toString().contains("--source-roots"), err.toString());
    }

    @Test
    void settingsGradleAloneAlsoTriggersTheHint() throws IOException {
        Files.writeString(repoRoot.resolve("settings.gradle"), "rootProject.name = 'demo'\n");

        run("doctor", "--repo", repoRoot.toString());

        assertTrue(err.toString().contains("Gradle"), err.toString());
    }

    /** A directory with no build file of any kind must not get an irrelevant Gradle suggestion. */
    @Test
    void aDirectoryWithNoBuildFileAtAllGetsNoGradleHint() {
        int exitCode = run("doctor", "--repo", repoRoot.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(err.toString().contains("no Maven module found"), err.toString());
        assertFalse(err.toString().contains("Gradle"), err.toString());
    }
}
