package dev.coverdict.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
 * mvn -Pmutation-it -pl coverdict-cli test}.
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
 * <p>Does not assert the mutation phase completes within the budget: D-58
 * found this environment's actual completion time for even a small,
 * diff-scoped target to be longer than a few minutes and left it as an
 * open question (see docs/DECISIONS.md) rather than guess at a root cause
 * further live debugging could not pin down safely. A clean {@code
 * MUTATION_BUDGET_EXCEEDED} timeout is accepted as passing - the
 * regression this guards against is the driver never starting at all, not
 * the mutation phase's own duration.
 */
class MutationRunnerIT {

    /** Short: this test only needs the driver to start and PIT to accept the run - not to finish. */
    private static final Duration BUDGET = Duration.ofSeconds(90);

    @Test
    void theDriverStartsAndPitAcceptsTheRunWithoutTheClasspathCascade() {
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
        List<String> targetClasses = List.of("dev.coverdict.analysis.binding.ChangedClassTargets*");

        try {
            Optional<MutationModuleEvidence> result = MutationRunner.run("coverdict-cli-it", repoRoot,
                classPathElements, codePaths, targetClasses, BUDGET);
            // Completed within the budget - the strongest possible pass.
            assertTrue(result.isPresent(), "expected PIT to find at least one mutable point in a real production class");
            assertFalse(result.get().methods().isEmpty(), "expected at least one mutated method");
        } catch (MutationCollectionException e) {
            // A clean timeout is an accepted outcome (see class javadoc) -
            // anything else (classpath/ClassNotFoundException/driver crash)
            // is exactly the regression this test exists to catch.
            String message = String.valueOf(e.getMessage());
            if (!message.contains("exceeded its") || !message.contains("budget")) {
                fail("Expected only a clean budget timeout, got: " + message, e);
            }
        }
    }
}
