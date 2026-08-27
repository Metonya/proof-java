package dev.coverdict.analysis.subprocess;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Narrows a set of target-class FQCN globs to same-package test-class globs
 * - the "test lives beside the class it tests" Maven/Gradle convention
 * {@code dev.coverdict.analysis.redundancy.TestLocator} already relies on
 * for path resolution.
 *
 * <p>D-59/D-63: originally {@code MutationDriver}-only, extracted here once
 * D-68's WTA dogfood evidence showed {@code PerTestDriver} needed the exact
 * same narrowing for a different reason. {@code MutationDriver} avoids
 * telling PIT that every test in the module (716 in coverdict's own repo)
 * is a candidate covering test, because {@code setFullMutationMatrix(true)}
 * re-gathers coverage per target method probed, and an unscoped candidate
 * set made that cost scale with (target methods) x (unscoped suite size).
 * {@code PerTestDriver} does not set that flag, so it never had that
 * specific blowup - but an unscoped {@code targetTests=[^.*$]} under a dev/
 * test classpath (this JVM's own runtime classpath, appended to every
 * driver's {@code -cp} by {@code SubprocessWorkspace.ownRuntimeClasspathEntries()})
 * makes PIT try to run every test class on that JVM's own classpath as a
 * candidate too - not just the target repo's - which is exactly what
 * {@code PlaygroundMutationIT} hit running under {@code -Pmutation-it}:
 * PIT discovered and tried to execute coverdict's own {@code MainTest}/
 * {@code PlaygroundFunctionalTest} alongside the playground fixture's real
 * tests, blowing well past the 120s per-test collection timeout.
 *
 * <p>Not a coverage-verified covering-test set (that would need L2's own
 * JaCoCo data, which a driver deriving the test set for L2 itself obviously
 * cannot have) - a massive, cheap narrowing from "everything reachable on
 * this classpath" to "plausibly relevant." Cross-package test coverage of a
 * target class remains a known, documented gap (see {@code
 * MutationDriver}'s original javadoc / docs/DECISIONS.md).
 */
public final class TestGlobs {

    private TestGlobs() {
    }

    /** @return {@code <package>.*} for every unique package among {@code targetClassGlobs}; {@code List.of("*")} (match everything) if none resolves to a named package. */
    public static List<String> samePackageGlobsFor(List<String> targetClassGlobs) {
        Set<String> packages = new LinkedHashSet<>();
        for (String targetClassGlob : targetClassGlobs) {
            String fqcn = targetClassGlob.endsWith("*")
                ? targetClassGlob.substring(0, targetClassGlob.length() - 1) : targetClassGlob;
            int lastDot = fqcn.lastIndexOf('.');
            if (lastDot > 0) {
                packages.add(fqcn.substring(0, lastDot));
            }
        }
        if (packages.isEmpty()) {
            return List.of("*"); // no resolvable package (default/unnamed package target) - fall back rather than match nothing
        }
        List<String> globs = new ArrayList<>();
        for (String pkg : packages) {
            globs.add(pkg + ".*");
        }
        return globs;
    }
}
