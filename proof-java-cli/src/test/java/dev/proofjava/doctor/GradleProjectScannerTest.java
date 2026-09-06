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

    /**
     * {@code includeFlat("sib")} means the project directory is {@code
     * ../sib} - a sibling of the repo root, which the repo-relative path
     * model cannot represent. Reporting it as the plain subdirectory
     * {@code sib} (what this scan used to do) names a directory that is
     * absent, or belongs to something else entirely.
     */
    @Test
    void includeFlatIsSkippedRatherThanReportedAtAWrongRoot() throws IOException {
        write("settings.gradle.kts", """
            rootProject.name = "demo"
            includeFlat("sib")
            """);
        writeSourceDir("sib/src/main/java"); // even if such a directory exists, it is not that project

        assertTrue(GradleProjectScanner.scan(repoRoot).isEmpty());
    }

    /**
     * Real settings-file text from Google's Now in Android. These are
     * `RepositoryContentDescriptor` methods inside `repositories { content
     * { } }` - artifact filters, not projects. Reading them as projects
     * produced a path containing `*`, which is not legal on Windows, so
     * `doctor` died with an InvalidPathException before printing anything
     * at all.
     */
    @Test
    void repositoryContentFiltersAreNotProjects() throws IOException {
        write("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    google {
                        content {
                            includeGroupByRegex("com\\\\.android.*")
                            includeGroupByRegex("androidx.*")
                            includeModule("com.example", "lib")
                            includeVersionByRegex("com.example", "lib", "1\\\\..*")
                        }
                    }
                }
            }
            rootProject.name = "demo"
            include(":app")
            """);
        writeSourceDir("app/src/main/java");

        assertEquals(List.of(new MavenModule("app", "app")), GradleProjectScanner.scan(repoRoot));
    }

    /**
     * Second layer for the same failure: even if some future settings shape
     * slips past the keyword pattern, a value that cannot name a directory
     * must cost at most a missing module - never the whole command.
     */
    @Test
    void aValueThatCannotNameADirectoryIsSkippedRatherThanCrashingTheScan() throws IOException {
        write("settings.gradle.kts", """
            include("glob*pattern")
            include(":real")
            """);

        assertEquals(List.of(new MavenModule("real", "real")), GradleProjectScanner.scan(repoRoot));
    }

    /** A composite build is a separate Gradle root with its own lifecycle, never a subproject of this one. */
    @Test
    void includeBuildIsNotTreatedAsASubproject() throws IOException {
        write("settings.gradle.kts", """
            rootProject.name = "demo"
            includeBuild("gradle/plugins")
            """);
        writeSourceDir("gradle/plugins/src/main/java");

        assertTrue(GradleProjectScanner.scan(repoRoot).isEmpty());
    }

    /**
     * The exclusions above are prefix-exact: a user-defined wrapper that
     * merely starts with the same letters is still a real include helper
     * and must keep working (junit-framework's own `includeProject` is the
     * reason wrapper support exists at all).
     */
    @Test
    void aWrapperWhoseNameOnlyStartsWithAnExcludedWordIsStillMatched() throws IOException {
        write("settings.gradle.kts", """
            rootProject.name = "demo"
            includeFlattenedModules(":core")
            """);

        assertEquals(List.of(new MavenModule("core", "core")), GradleProjectScanner.scan(repoRoot));
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
