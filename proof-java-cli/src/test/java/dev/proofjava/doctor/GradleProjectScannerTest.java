package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real settings.gradle(.kts) text, not a hand-built model - mirrors {@link
 * MavenProjectScannerTest}'s own precedent of testing against real document
 * text rather than mocking the parse.
 */
class GradleProjectScannerTest {

    @TempDir
    Path repoRoot;

    private void write(String relativePath, String content) throws IOException {
        Path file = repoRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void writeSourceDir(String relativePath) throws IOException {
        Files.createDirectories(repoRoot.resolve(relativePath));
    }

    @Test
    void aSingleModuleProjectWithNoSettingsFileIsStillOneModule() throws IOException {
        write("build.gradle.kts", "plugins { java }\n");
        writeSourceDir("src/main/java");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(1, modules.size());
        assertEquals(".", modules.get(0).root());
    }

    @Test
    void noBuildFileAtAllProducesNoModules() {
        assertTrue(GradleProjectScanner.scan(repoRoot).isEmpty());
    }

    @Test
    void aRootProjectWithNoOwnSourcesAndNoSubprojectsProducesNoModules() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"empty\"\n");

        assertTrue(GradleProjectScanner.scan(repoRoot).isEmpty());
    }

    @Test
    void ktsIncludeCallsAreDiscoveredAsSubprojectModules() throws IOException {
        write("settings.gradle.kts", """
            rootProject.name = "demo"
            include(":core", ":extras")
            """);
        writeSourceDir("core/src/main/java");
        writeSourceDir("extras/src/main/java");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("core", "core"), new MavenModule("extras", "extras")), modules);
    }

    @Test
    void groovyIncludeCallsAreDiscoveredTheSameWay() throws IOException {
        write("settings.gradle", "include ':gson', ':gson:extras'\n");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("gson", "gson"), new MavenModule("extras", "gson/extras")), modules);
    }

    @Test
    void aNestedGradlePathKeepsEverySegmentInTheRoot() throws IOException {
        write("settings.gradle.kts", "include(\":modules:service-a\")\n");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("service-a", "modules/service-a")), modules);
    }

    /** The root project is only reported when it looks like a real Java module of its own, never as noise for a pure umbrella build. */
    @Test
    void aRootProjectWithItsOwnSourcesIsIncludedAlongsideSubprojects() throws IOException {
        write("settings.gradle.kts", """
            rootProject.name = "demo"
            include(":core")
            """);
        writeSourceDir("src/main/java");
        writeSourceDir("core/src/main/java");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("demo", "."), new MavenModule("core", "core")), modules);
    }

    @Test
    void aRootProjectWithNoOwnSourcesIsExcludedWhenSubprojectsExist() throws IOException {
        write("settings.gradle.kts", """
            rootProject.name = "demo"
            include(":core")
            """);
        writeSourceDir("core/src/main/java");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("core", "core")), modules);
    }

    /** A subproject path escaping the repo root (SECURITY-POLICY.md #4 precedent, same as MavenProjectScanner) is never followed. */
    @Test
    void aSubprojectPathEscapingTheRepoRootIsNotFollowed() throws IOException {
        write("settings.gradle.kts", "include(\":..:outside\")\n");

        assertTrue(GradleProjectScanner.scan(repoRoot).isEmpty());
    }

    @Test
    void ktsTakesPriorityOverGroovyWhenBothExist() throws IOException {
        write("settings.gradle.kts", "include(\":from-kts\")\n");
        write("settings.gradle", "include ':from-groovy'\n");
        writeSourceDir("from-kts/src/main/java");

        List<MavenModule> modules = GradleProjectScanner.scan(repoRoot);

        assertEquals(List.of(new MavenModule("from-kts", "from-kts")), modules);
    }
}
