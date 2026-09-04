package dev.proofjava.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Drives a real PIT mutation run against coverdict's own repository - not
 * bound to the default {@code mvn verify} gate (minutes, not milliseconds;
 * needs a real classpath), only to the {@code mutation-it} profile: {@code
 * mvn -Pmutation-it -pl proof-java-cli test}.
 *
 * <p>This is the concrete check for D-57 and D-58: the M2 spike's {@code
 * MINION_DIED}/{@code agent library failed to init: instrument} cascade
 * (root cause: the spike's own hand-built driver classpath was missing
 * {@code commons-text}) and this session's own {@code
 * NoClassDefFoundError: org.pitest.mutationtest.config.ReportOptions}
 * (root cause: {@code SubprocessWorkspace}'s pre-D-58 self-located-class
 * classpath resolved to a bare compiled-output directory under Maven,
 * carrying none of PIT's separate dependency jars) must not reproduce
 * here - the driver process either finds PIT and its own classes on the
 * classpath and starts cleanly, or it doesn't and this test fails loudly.
 *
 * <p>Also the concrete regression check for D-6x's fix to D-59: before that
 * fix, {@code MutationDriver} scoped {@code targetTests} to an unscoped
 * {@code "*"}, telling PIT every one of this repo's 716 test classes was a
 * covering-test candidate. Under {@code setFullMutationMatrix(true)} that
 * forced PIT to re-gather full-suite coverage once per target method being
 * probed (a live capture showed the same 19-test class re-executed six
 * times in one second for this exact target) - this run reliably hit the
 * 90s budget and never completed, root cause traced live via a verbose PIT
 * capture and confirmed fixed by narrowing {@code targetTests} to the
 * target class's own package. This test now asserts the run actually
 * *completes* (well under a second in practice) rather than accepting a
 * clean timeout as the best available signal - a regression back to
 * unscoped tests would show up here as a real timeout again, not just as
 * slower CI.
 */
class MutationRunnerIT {

    /**
     * Generous relative to the ~1s this run now takes post-D-6x fix, but
     * still bounded - a real hang or a regression back to D-59's unscoped-
     * targetTests behavior should fail this test, not silently eat CI time.
     */
    private static final Duration BUDGET = Duration.ofSeconds(30);

    @Test
    void theDriverStartsAndPitCompletesARealMutationRun() {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        List<String> classPathElements = new ArrayList<>();
        List<String> codePaths = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            classPathElements.add(entry);
            if (new File(entry).isDirectory()) {
                codePaths.add(entry);
            }
        }
        assertFalse(codePaths.isEmpty(), "expected at least one compiled-output directory on java.class.path");

        // A real class with actual branching logic (not a trivial record
        // accessor) - ChangedClassTargets.forEachMappedFile has classification
        // and prefix-matching conditionals gregor's RETURNS/VOID_METHOD_CALLS
        // mutators can meaningfully act on.
        List<String> targetClasses = List.of("dev.proofjava.analysis.binding.ChangedClassTargets*");

        Optional<MutationModuleEvidence> result = MutationRunner.run("proof-java-cli-it", repoRoot,
            classPathElements, codePaths, targetClasses, BUDGET);
        assertTrue(result.isPresent(), "expected PIT to find at least one mutable point in a real production class");
        assertFalse(result.get().methods().isEmpty(), "expected at least one mutated method");
    }
}
