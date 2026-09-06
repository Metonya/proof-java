package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.RepoPaths;
import dev.proofjava.analysis.subprocess.ClasspathListFile;

/**
 * Exercises {@link GradleClasspathFixer} without a real {@code gradlew}
 * subprocess: a {@link GradleClient} subclass stands in for the wrapper
 * invocation and, reading the {@code -PproofClasspathOutputFile=...} arg
 * this class always passes, writes the dump file a real init-script-driven
 * {@code proofDumpClasspath} task would have produced - same "fake the
 * boundary, test the logic on our side of it" shape as {@link
 * ClasspathFixerTest}.
 */
class GradleClasspathFixerTest {

    @TempDir
    Path repoRoot;

    private static final MavenModule ROOT_MODULE = new MavenModule("app", ".");
    private static final MavenModule SUB_MODULE = new MavenModule("core", "core");

    private final class FakeGradleClient extends GradleClient {
        private final boolean succeed;
        private final List<String> dumpLines;
        private String lastTaskPath;

        FakeGradleClient(boolean succeed, List<String> dumpLines) {
            super(repoRoot, Duration.ofSeconds(5));
            this.succeed = succeed;
            this.dumpLines = dumpLines;
        }

        @Override
        public Result run(String... args) {
            lastTaskPath = args[args.length - 1];
            if (!succeed) {
                return new Result(false, "simulated gradlew failure");
            }
            String outputFileArg = null;
            for (String arg : args) {
                if (arg.startsWith("-P" + GradleClasspathFixer.OUTPUT_FILE_PROPERTY + "=")) {
                    outputFileArg = arg.substring(arg.indexOf('=') + 1);
                }
            }
            try {
                Files.writeString(Path.of(outputFileArg), String.join(System.lineSeparator(), dumpLines), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOExceptionForTest(e);
            }
            return new Result(true, null);
        }
    }

    private static final class UncheckedIOExceptionForTest extends RuntimeException {
        UncheckedIOExceptionForTest(IOException cause) {
            super(cause);
        }
    }

    @Test
    void writesBothClasspathFilesWithIdenticalContentFromGradlesDump() throws IOException {
        String dependencyJar = repoRoot.resolve("fake-dep-1.0.jar").toString();
        Path mainClassesDir = repoRoot.resolve("build/classes/java/main");
        Files.createDirectories(mainClassesDir);
        String mainClasses = mainClassesDir.toString();
        FakeGradleClient client = new FakeGradleClient(true, List.of(mainClasses, dependencyJar));

        ClasspathFixer.FixResult result = GradleClasspathFixer.fix(client, repoRoot, ROOT_MODULE);
        assertTrue(result.ok(), result.problem());

        String perTest = readFile(repoRoot.resolve(GradleClasspathFixer.PER_TEST_CLASSPATH_FILE));
        String mutation = readFile(repoRoot.resolve(GradleClasspathFixer.MUTATION_CLASSPATH_FILE));
        assertEquals(perTest, mutation, "L2 and L3 must see the same classpath for the same module");
        assertTrue(perTest.contains(mainClasses));
        assertTrue(perTest.contains(dependencyJar));
    }

    @Test
    void theRootModuleUsesTheBareTaskNameNotAGradlePath() {
        FakeGradleClient client = new FakeGradleClient(true, List.of(repoRoot.resolve("build/classes/java/main").toString()));

        GradleClasspathFixer.fix(client, repoRoot, ROOT_MODULE);

        assertEquals(GradleClasspathFixer.DUMP_TASK_NAME, client.lastTaskPath);
    }

    @Test
    void aSubprojectModuleGetsAColonQualifiedGradleTaskPath() {
        FakeGradleClient client = new FakeGradleClient(true, List.of(repoRoot.resolve("core/build/classes/java/main").toString()));

        GradleClasspathFixer.fix(client, repoRoot, SUB_MODULE);

        assertEquals(":core:" + GradleClasspathFixer.DUMP_TASK_NAME, client.lastTaskPath);
    }

    @Test
    void theGeneratedListIsValidatedAndUsableByAnalyzeImmediately() throws IOException {
        String mainClasses = repoRoot.resolve("core/build/classes/java/main").toString();
        Files.createDirectories(Path.of(mainClasses));
        FakeGradleClient client = new FakeGradleClient(true, List.of(mainClasses));

        GradleClasspathFixer.fix(client, repoRoot, SUB_MODULE);

        String repoRelative = RepoPaths.join(SUB_MODULE.root(), GradleClasspathFixer.PER_TEST_CLASSPATH_FILE);
        ClasspathListFile parsed = ClasspathListFile.load(repoRoot, repoRelative);
        assertTrue(parsed.ok(), "the file this class writes must pass the exact validation --mutation-report applies");
    }

    @Test
    void aGradleFailurePropagatesAsAProblemRatherThanWritingAnEmptyList() {
        FakeGradleClient client = new FakeGradleClient(false, List.of());

        ClasspathFixer.FixResult result = GradleClasspathFixer.fix(client, repoRoot, ROOT_MODULE);

        assertFalse(result.ok());
        assertTrue(result.problem().contains("Gradle classpath dump failed"), result.problem());
        assertFalse(Files.exists(repoRoot.resolve(GradleClasspathFixer.PER_TEST_CLASSPATH_FILE)));
    }

    @Test
    void anEmptyDumpIsReportedRatherThanWritingAnEmptyList() {
        FakeGradleClient client = new FakeGradleClient(true, List.of());

        ClasspathFixer.FixResult result = GradleClasspathFixer.fix(client, repoRoot, ROOT_MODULE);

        assertFalse(result.ok());
        assertTrue(result.problem().contains("empty classpath"), result.problem());
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOExceptionForTest(e);
        }
    }
}
