package dev.coverdict.analysis.mutation;

import java.nio.file.Path;
import java.util.List;

import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.subprocess.ClasspathListFile;

/**
 * Reads {@code --mutation-classpath <id>=<file>}: one classpath entry per
 * line, the same list-file shape as {@link
 * dev.coverdict.analysis.pertest.PerTestClasspathLoader}'s {@code
 * --per-test-classpath} - a separate loader rather than a shared one so
 * this evidence layer's own warning code ({@code MUTATION_CLASSPATH_MISSING})
 * and message never get confused with L2's. Both delegate their actual
 * parsing to {@link ClasspathListFile}.
 *
 * <p>Entries that are directories on disk (a module's compiled output) are
 * also collected as {@code codePaths} - {@code ReportOptions.setCodePaths}
 * distinguishes "code under test" from dependency jars on the same
 * classpath, same as the L2 loader.
 */
public final class MutationClasspathLoader {

    private static final String WARNING_CODE = "MUTATION_CLASSPATH_MISSING";
    private static final String EVIDENCE_LABEL = "Mutation";

    private MutationClasspathLoader() {
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
