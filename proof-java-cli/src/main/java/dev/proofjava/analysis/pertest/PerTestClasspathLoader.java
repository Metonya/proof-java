package dev.proofjava.analysis.pertest;

import java.nio.file.Path;
import java.util.List;

import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.subprocess.ClasspathListFile;

/**
 * Reads {@code --per-test-classpath <id>=<file>}: one classpath entry per
 * line, the same list-file shape as {@link
 * dev.proofjava.analysis.oracle.ClasspathLoader}'s {@code --classpath} - but
 * a different purpose. {@code --classpath} is an optional symbol-resolution
 * aid whose absence never blocks anything (D-17); this is the exact runtime
 * classpath PIT needs to run the module's tests, so a missing, unreadable,
 * or code-path-less file skips the module's per-test evidence entirely
 * (a {@code PER_TEST_CLASSPATH_MISSING} warning) rather than degrading it.
 *
 * <p>Entries that are directories on disk (a module's compiled output, e.g.
 * {@code target/classes} or {@code target/test-classes}) are also collected
 * as {@code codePaths} (Faz2aSpike's {@code mainClasses} -
 * {@code ReportOptions.setCodePaths} distinguishes "code under test" from
 * dependency jars on the same classpath). Test-output directories are
 * over-included by this rule too; harmless here since {@link PerTestRunner}
 * never runs a mutation pre-scan (D-51's zero-units trap is specific to that
 * phase) and always calls {@code calculateCoverage} with an accept-all
 * predicate. proof-java has no Maven/Gradle build-output convention to guess
 * from instead (D-51 rejects build-tool detection outright) - the caller
 * states its classpath explicitly, same as {@code --classpath}.
 */
public final class PerTestClasspathLoader {

    private static final String WARNING_CODE = "PER_TEST_CLASSPATH_MISSING";
    private static final String EVIDENCE_LABEL = "Per-test";

    private PerTestClasspathLoader() {
    }

    public static Result load(Path repoRoot, String moduleId, String listFilePath) {
        ClasspathListFile parsed = ClasspathListFile.load(repoRoot, listFilePath);
        if (!parsed.ok()) {
            return new Result(List.of(), List.of(),
                List.of(parsed.toWarning(WARNING_CODE, EVIDENCE_LABEL, moduleId, listFilePath)));
        }
        return new Result(parsed.classPathElements(), parsed.codePaths(), List.of());
    }

    /** {@code warnings} is non-empty exactly when {@code classPathElements}/{@code codePaths} are both empty. */
    public record Result(List<String> classPathElements, List<String> codePaths, List<AnalysisReason> warnings) {
    }
}
