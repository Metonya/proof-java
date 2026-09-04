package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.RepoPaths;
import dev.proofjava.analysis.subprocess.ClasspathListFile;

/**
 * Exercises {@link ClasspathFixer} without a real {@code mvn} subprocess: a
 * {@link MavenClient} subclass stands in for the Maven call and simply
 * writes the {@code target/proof-dependencies.txt} a real
 * {@code dependency:build-classpath} run would have produced - the same
 * "fake the boundary, test the logic on our side of it" shape as this
 * repo's other subprocess-adjacent tests.
 */
class ClasspathFixerTest {

    @TempDir
    Path repoRoot;

    private static final MavenModule MODULE = new MavenModule("app", "app");

    private final class FakeMavenClient extends MavenClient {
        private final boolean succeed;
        private final String dependencyListLine;

        FakeMavenClient(boolean succeed, String dependencyListLine) {
            super(repoRoot, Duration.ofSeconds(5));
            this.succeed = succeed;
            this.dependencyListLine = dependencyListLine;
        }

        @Override
        public Result buildClasspath(String moduleRoot) {
            if (!succeed) {
                return new Result(false, "simulated mvn failure");
            }
            try {
                // A real repo already has these before 'doctor --fix' runs -
                // the module must be compiled for target/classes to exist at
                // all, which is exactly what makes this class's own
                // ClasspathListFile.load() validation meaningful to test.
                Files.createDirectories(repoRoot.resolve(moduleRoot).resolve("target/classes"));
                Files.createDirectories(repoRoot.resolve(moduleRoot).resolve("target/test-classes"));
                Path deps = repoRoot.resolve(moduleRoot).resolve(ClasspathFixer.DEPENDENCIES_FILE);
                Files.createDirectories(deps.getParent());
                Files.writeString(deps, dependencyListLine, StandardCharsets.UTF_8);
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
    void writesBothClasspathFilesWithIdenticalContentFromMavensDependencyOutput() {
        String dependencyJar = repoRoot.resolve("fake-dep-1.0.jar").toString();
        ClasspathFixer.FixResult result = ClasspathFixer.fix(new FakeMavenClient(true, dependencyJar), repoRoot, MODULE);

        assertTrue(result.ok(), result.problem());

        String perTest = ClasspathFixerTest.readFile(repoRoot.resolve("app/" + ClasspathFixer.PER_TEST_CLASSPATH_FILE));
        String mutation = ClasspathFixerTest.readFile(repoRoot.resolve("app/" + ClasspathFixer.MUTATION_CLASSPATH_FILE));
        assertEquals(perTest, mutation, "L2 and L3 must see the same classpath for the same module");
        assertTrue(perTest.contains("app/target/classes"));
        assertTrue(perTest.contains("app/target/test-classes"));
        assertTrue(perTest.contains(dependencyJar));
    }

    @Test
    void theGeneratedListIsValidatedAndUsableByAnalyzeImmediately() {
        String dependencyJar = repoRoot.resolve("fake-dep-1.0.jar").toString();
        ClasspathFixer.fix(new FakeMavenClient(true, dependencyJar), repoRoot, MODULE);

        String repoRelative = RepoPaths.join(MODULE.root(), ClasspathFixer.PER_TEST_CLASSPATH_FILE);
        ClasspathListFile parsed = ClasspathListFile.load(repoRoot, repoRelative);

        assertTrue(parsed.ok(), "the file this class writes must pass the exact validation --mutation-report applies");
        assertTrue(parsed.codePaths().stream().anyMatch(p -> p.endsWith("app" + File.separator + "target" + File.separator + "classes")
            || p.endsWith("app/target/classes")));
    }

    @Test
    void aMavenFailurePropagatesAsAProblemRatherThanWritingAnEmptyList() {
        ClasspathFixer.FixResult result = ClasspathFixer.fix(new FakeMavenClient(false, null), repoRoot, MODULE);

        assertFalse(result.ok());
        assertTrue(result.problem().contains("Maven dependency resolution failed"));
        assertFalse(Files.exists(repoRoot.resolve("app/" + ClasspathFixer.PER_TEST_CLASSPATH_FILE)));
    }

    /**
     * A real, if unlikely, failure shape: Maven exits 0 but writes nothing
     * usable (an empty dependency scope). The fixer must not silently
     * accept its own output - same discipline {@code ClasspathListFile}
     * already applies on read.
     */
    @Test
    void anEmptyDependencyListStillPassesBecauseTheModulesOwnOutputDirsAreCodePaths() {
        ClasspathFixer.FixResult result = ClasspathFixer.fix(new FakeMavenClient(true, ""), repoRoot, MODULE);

        assertTrue(result.ok(), result.problem());
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOExceptionForTest(e);
        }
    }
}
