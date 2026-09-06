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
     * D-96 (superseded): before Gradle support, a Gradle-only repository
     * (no pom.xml anywhere, a bare build.gradle.kts) got the same bare "no
     * Maven module found" message as a directory with no build file at
     * all. {@code doctor} now discovers and actually diagnoses a real
     * Gradle module instead of just hinting - this exercises that end to
     * end: a real (if minimal) Gradle project with a source root but no
     * build output/report yet, so it must be found, diagnosed, and
     * reported as blocked, never mistaken for "no module found".
     */
    @Test
    void aGradleOnlyRepoIsDiscoveredAndDiagnosedNotJustHintedAt() throws IOException {
        Files.writeString(repoRoot.resolve("build.gradle.kts"), "plugins { java }\n");
        Files.createDirectories(repoRoot.resolve("src/main/java"));

        int exitCode = run("doctor", "--repo", repoRoot.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode, err.toString());
        assertFalse(out.toString().contains("no Maven or Gradle module found"), out.toString());
        assertTrue(out.toString().contains("build/reports/jacoco/test/jacocoTestReport.xml not found"), out.toString());
    }

    /**
     * A settings.gradle(.kts) with no source directories and no declared
     * subprojects is a real Gradle marker with genuinely nothing to
     * diagnose yet - the narrower remaining case the old D-96 hint now
     * covers (DoctorCommand's own noModuleHint).
     */
    @Test
    void aGradleMarkerWithNoResolvableModuleStillGetsAGradleAwareMessage() throws IOException {
        Files.writeString(repoRoot.resolve("settings.gradle"), "rootProject.name = 'demo'\n");

        int exitCode = run("doctor", "--repo", repoRoot.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(err.toString().contains("no Maven or Gradle module found"), err.toString());
        assertTrue(err.toString().contains("no module could be resolved from it"), err.toString());
    }

    /** A directory with no build file of any kind must not get an irrelevant Gradle-specific hint appended. */
    @Test
    void aDirectoryWithNoBuildFileAtAllGetsNoGradleHint() {
        int exitCode = run("doctor", "--repo", repoRoot.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(err.toString().contains("no Maven or Gradle module found"), err.toString());
        assertFalse(err.toString().contains("no module could be resolved from it"), err.toString());
    }
}
