package dev.proofjava.doctor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.proofjava.analysis.model.RepoPaths;
import dev.proofjava.analysis.subprocess.ClasspathListFile;

/**
 * Regenerates one Gradle module's L2/L3 classpath lists, the Gradle
 * counterpart of {@link ClasspathFixer} - same output shape ({@code
 * --per-test-classpath}/{@code --mutation-classpath} take an identical
 * plain-text list either way), different mechanism underneath because
 * Gradle has no equivalent of {@code mvn dependency:build-classpath}
 * (D-53).
 *
 * <p>Uses a {@code --init-script}, external to the target repo and never
 * touching its {@code build.gradle(.kts)} (verified empirically in D-53:
 * {@code git status} stayed empty throughout). Two Gradle-specific
 * obstacles, both solved the way D-53 found working:
 * <ul>
 *   <li>Isolated Projects rejects an init script's {@code allprojects{}}/
 *       {@code subprojects{}} cross-project access - solved with {@code
 *       gradle.beforeProject { }} plus that project's own {@code
 *       afterEvaluate { }}, which stays within the one project Isolated
 *       Projects is inspecting.</li>
 *   <li>Configuration Cache (mandatory once Isolated Projects is on)
 *       rejects capturing {@code Project}/{@code configurations} state
 *       inside a task's {@code doLast} - solved by capturing the resolved
 *       {@code FileCollection} at configuration time and only carrying
 *       that reference (not the project) into execution.</li>
 * </ul>
 */
public final class GradleClasspathFixer {

    static final String OUTPUT_FILE_PROPERTY = "proofClasspathOutputFile";
    static final String DUMP_TASK_NAME = "proofDumpClasspath";
    static final String PER_TEST_CLASSPATH_FILE = "build/proof-per-test-classpath.txt";
    static final String MUTATION_CLASSPATH_FILE = "build/proof-mutation-classpath.txt";

    private static final String INIT_SCRIPT = """
        gradle.beforeProject { project ->
            project.afterEvaluate {
                if (!project.plugins.hasPlugin('java')) {
                    return
                }
                def testRuntime = project.configurations.findByName('testRuntimeClasspath')
                if (testRuntime == null) {
                    return
                }
                // Captured now (configuration time) - Configuration Cache forbids
                // touching `project`/`configurations` again from inside doLast.
                // This includes `project.findProperty(...)` - found live against
                // a real Isolated-Projects repo (junit-framework): leaving that
                // call inside doLast fails with "cannot serialize object of type
                // DefaultProject" even though every other capture here was
                // already configuration-time-only.
                def dependencyFiles = testRuntime
                def mainOutputDirs = project.sourceSets.main.output.classesDirs.files
                def mainResourcesDir = project.sourceSets.main.output.resourcesDir
                def testOutputDirs = project.sourceSets.test.output.classesDirs.files
                def testResourcesDir = project.sourceSets.test.output.resourcesDir
                def outputProperty = project.findProperty('proofClasspathOutputFile')

                project.tasks.register('proofDumpClasspath') {
                    dependsOn testRuntime
                    doLast {
                        if (outputProperty == null) {
                            return
                        }
                        def out = new File(outputProperty as String)
                        out.parentFile.mkdirs()
                        def lines = []
                        mainOutputDirs.each { lines << it.absolutePath }
                        if (mainResourcesDir != null) { lines << mainResourcesDir.absolutePath }
                        testOutputDirs.each { lines << it.absolutePath }
                        if (testResourcesDir != null) { lines << testResourcesDir.absolutePath }
                        dependencyFiles.each { lines << it.absolutePath }
                        out.text = lines.join(System.lineSeparator())
                    }
                }
            }
        }
        """;

    private GradleClasspathFixer() {
    }

    public static ClasspathFixer.FixResult fix(Path repoRoot, MavenModule module) {
        return fix(new GradleClient(repoRoot), repoRoot, module);
    }

    static ClasspathFixer.FixResult fix(GradleClient gradle, Path repoRoot, MavenModule module) {
        Path initScript;
        try {
            initScript = Files.createTempFile("proof-gradle-classpath-", ".init.gradle");
            Files.writeString(initScript, INIT_SCRIPT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ClasspathFixer.FixResult(false, "could not write the Gradle init script: " + e.getMessage());
        }

        Path dumpTarget;
        try {
            dumpTarget = Files.createTempFile("proof-gradle-dump-", ".txt");
        } catch (IOException e) {
            deleteQuietly(initScript);
            return new ClasspathFixer.FixResult(false, "could not create a temp file for Gradle's classpath dump: " + e.getMessage());
        }

        try {
            String taskPath = taskPathFor(module.root());
            GradleClient.Result result = gradle.run(
                "--init-script", initScript.toString(),
                "-P" + OUTPUT_FILE_PROPERTY + "=" + dumpTarget.toAbsolutePath(),
                "-q", taskPath);
            if (!result.ok()) {
                return new ClasspathFixer.FixResult(false, "Gradle classpath dump failed: " + result.problem());
            }

            List<String> entries;
            try {
                entries = Files.readAllLines(dumpTarget, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return new ClasspathFixer.FixResult(false, "could not read Gradle's dumped classpath: " + e.getMessage());
            }
            if (entries.isEmpty()) {
                return new ClasspathFixer.FixResult(false, "'" + taskPath + "' produced an empty classpath - is "
                    + "the module id/root correct, and does it apply the 'java' plugin?");
            }

            String perTestRelative = RepoPaths.join(module.root(), PER_TEST_CLASSPATH_FILE);
            String mutationRelative = RepoPaths.join(module.root(), MUTATION_CLASSPATH_FILE);
            try {
                writeClasspathFile(repoRoot.resolve(perTestRelative), entries);
                writeClasspathFile(repoRoot.resolve(mutationRelative), entries);
            } catch (IOException e) {
                return new ClasspathFixer.FixResult(false, "could not write the generated classpath list: " + e.getMessage());
            }

            ClasspathListFile parsed = ClasspathListFile.load(repoRoot, perTestRelative);
            if (!parsed.ok()) {
                return new ClasspathFixer.FixResult(false, "generated classpath list " + perTestRelative + " still " + parsed.problem());
            }
            return new ClasspathFixer.FixResult(true, null);
        } finally {
            deleteQuietly(initScript);
            deleteQuietly(dumpTarget);
        }
    }

    /** {@code "."} (the root project) -> {@code proofDumpClasspath}; {@code "core/sub"} -> {@code :core:sub:proofDumpClasspath}. */
    static String taskPathFor(String moduleRoot) {
        if (moduleRoot == null || moduleRoot.isEmpty() || moduleRoot.equals(".")) {
            return DUMP_TASK_NAME;
        }
        String gradlePath = moduleRoot.replace('/', ':');
        return ":" + gradlePath + ":" + DUMP_TASK_NAME;
    }

    private static void writeClasspathFile(Path file, List<String> lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best-effort cleanup of a temp file - not worth failing the fix over
        }
    }
}
