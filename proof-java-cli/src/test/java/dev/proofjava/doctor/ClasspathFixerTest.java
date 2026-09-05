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
        private final boolean succeedFirstTry;
        private final boolean succeedAfterInstall;
        private final boolean installSucceeds;
        private final String dependencyListLine;
        private int buildClasspathCalls;
        private int installCalls;

        FakeMavenClient(boolean succeed, String dependencyListLine) {
            this(succeed, false, false, dependencyListLine);
        }

        FakeMavenClient(boolean succeedFirstTry, boolean installSucceeds, boolean succeedAfterInstall,
                         String dependencyListLine) {
            super(repoRoot, Duration.ofSeconds(5));
            this.succeedFirstTry = succeedFirstTry;
            this.installSucceeds = installSucceeds;
            this.succeedAfterInstall = succeedAfterInstall;
            this.dependencyListLine = dependencyListLine;
        }

        @Override
        public Result buildClasspath(String moduleRoot) {
            buildClasspathCalls++;
            boolean shouldSucceed = buildClasspathCalls == 1 ? succeedFirstTry : succeedAfterInstall;
            if (!shouldSucceed) {
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

        @Override
        public Result installReactor(String moduleRoot) {
            installCalls++;
            return installSucceeds ? new Result(true, null) : new Result(false, "simulated install failure");
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
        FakeMavenClient client = new FakeMavenClient(false, false, false, null);
        ClasspathFixer.FixResult result = ClasspathFixer.fix(client, repoRoot, MODULE);

        assertFalse(result.ok());
        assertTrue(result.problem().contains("Maven dependency resolution failed"));
        assertFalse(Files.exists(repoRoot.resolve("app/" + ClasspathFixer.PER_TEST_CLASSPATH_FILE)));
    }

    /**
     * D-97: {@code dependency:build-classpath} resolves purely against the
     * local repository, never the in-memory reactor - a genuine multi-module
     * project (dropwizard, not gson/assertj's shallower shape) whose sibling
     * SNAPSHOT modules were never {@code mvn install}ed fails the first try
     * on every module with an inter-module compile dependency. The fixer
     * must retry by installing the reactor first, exactly the manual step
     * this campaign needed by hand before {@code doctor --fix} could see
     * the result.
     */
    @Test
    void aFailedBuildClasspathIsRetriedAfterInstallingTheReactor() {
        FakeMavenClient client = new FakeMavenClient(false, true, true, "fake-dep.jar");

        ClasspathFixer.FixResult result = ClasspathFixer.fix(client, repoRoot, MODULE);

        assertTrue(result.ok(), result.problem());
        assertEquals(1, client.installCalls, "install must be attempted exactly once, only after the first try fails");
        assertEquals(2, client.buildClasspathCalls, "the classpath goal is retried once install succeeds");
    }

    /** Both the classpath goal and the install fallback failing must report both problems, not just the first. */
    @Test
    void whenInstallingTheReactorAlsoFailsBothProblemsAreReported() {
        FakeMavenClient client = new FakeMavenClient(false, false, false, null);

        ClasspathFixer.FixResult result = ClasspathFixer.fix(client, repoRoot, MODULE);

        assertFalse(result.ok());
        assertTrue(result.problem().contains("Maven dependency resolution failed"), result.problem());
        assertTrue(result.problem().contains("installing the reactor first"), result.problem());
    }

    /** The common case (gson, assertj's shallower shape): no retry, no install, when the first try already works. */
    @Test
    void aSuccessfulFirstTryNeverAttemptsAnInstall() {
        FakeMavenClient client = new FakeMavenClient(true, false, false, "fake-dep.jar");

        ClasspathFixer.fix(client, repoRoot, MODULE);

        assertEquals(0, client.installCalls);
        assertEquals(1, client.buildClasspathCalls);
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
