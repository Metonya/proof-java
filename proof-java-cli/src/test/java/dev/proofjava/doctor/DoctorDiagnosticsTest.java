package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Each check exists because a real WTA dogfood round hit exactly the failure it now catches - see class javadoc on {@link DoctorDiagnostics}. */
class DoctorDiagnosticsTest {

    @TempDir
    Path repoRoot;

    private static final MavenModule MODULE = new MavenModule("app", "app");

    private void write(String relative, String content) throws IOException {
        Path file = repoRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void touch(String relative, Instant time) throws IOException {
        Path file = repoRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.writeString(file, "x", StandardCharsets.UTF_8);
        }
        Files.setLastModifiedTime(file, FileTime.from(time));
    }

    @Test
    void aModuleWithNoBuildAtAllReportsMissingReportAsABlocker() {
        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        assertTrue(d.hasBlocker());
        assertNull(d.jacocoReportPath());
        assertTrue(d.checks().stream().anyMatch(c -> c.code().equals("JACOCO_REPORT_MISSING")));
    }

    @Test
    void aFreshReportAfterCompiledOutputIsOk() throws IOException {
        Instant early = Instant.now().minusSeconds(60);
        Instant late = Instant.now();
        touch("app/target/classes/com/example/Foo.class", early);
        touch("app/target/site/jacoco/jacoco.xml", late);

        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        assertFalse(d.hasBlocker());
        assertEquals("app/target/site/jacoco/jacoco.xml", d.jacocoReportPath());
    }

    /**
     * Real bug this catches: WTA's first dogfood round built once, then
     * rebuilt code without regenerating the coverage report, and got a
     * silently-optimistic new-code number from the stale report instead of
     * a warning up front.
     */
    @Test
    void aReportOlderThanCompiledOutputIsABlocker() throws IOException {
        Instant early = Instant.now().minusSeconds(60);
        Instant late = Instant.now();
        touch("app/target/site/jacoco/jacoco.xml", early);
        touch("app/target/classes/com/example/Foo.class", late);

        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        assertTrue(d.hasBlocker());
        assertTrue(d.checks().stream().anyMatch(c -> c.code().equals("JACOCO_REPORT_STALE")));
    }

    @Test
    void generatedSourcesOutsideSrcMainJavaAreFlaggedAsAWarningNotABlocker() throws IOException {
        write("app/target/generated-sources/annotations/com/example/FooImpl.java", "class FooImpl {}");
        touch("app/target/site/jacoco/jacoco.xml", Instant.now());

        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        DoctorCheck check = d.checks().stream()
            .filter(c -> c.code().equals("GENERATED_SOURCES_FOUND"))
            .findFirst().orElseThrow();
        assertEquals(CheckStatus.WARN, check.status());
    }

    @Test
    void aMissingClasspathListIsAWarnNotABlockerBecauseL2L3IsOptIn() throws IOException {
        touch("app/target/site/jacoco/jacoco.xml", Instant.now());

        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        assertFalse(d.hasBlocker());
        assertNull(d.perTestClasspath());
    }

    /**
     * The exact WTA dogfood shape: a classpath list file exists (so a naive
     * "file present" check would call this fine) but contains no usable
     * code path, which silently produced zero L2/L3 evidence with no
     * warning until D-64.
     */
    @Test
    void aClasspathListWithNoCodePathIsABlocker() throws IOException {
        touch("app/target/site/jacoco/jacoco.xml", Instant.now());
        write("app/target/proof-per-test-classpath.txt", "\n  \n# comment\n");

        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        assertTrue(d.hasBlocker());
        assertTrue(d.checks().stream().anyMatch(c -> c.code().equals("PER_TEST_CLASSPATH_EMPTY")));
        assertNull(d.perTestClasspath());
    }

    @Test
    void aValidClasspathListIsSurfacedForTheRendererToUse() throws IOException {
        Instant early = Instant.now().minusSeconds(60);
        Instant late = Instant.now();
        touch("app/target/classes/com/example/Foo.class", early);
        touch("app/target/site/jacoco/jacoco.xml", late);
        write("app/target/proof-per-test-classpath.txt", "app/target/classes\n");

        ModuleDiagnosis d = DoctorDiagnostics.diagnose(repoRoot, MODULE);

        assertFalse(d.hasBlocker());
        assertEquals("app/target/proof-per-test-classpath.txt", d.perTestClasspath());
    }
}
