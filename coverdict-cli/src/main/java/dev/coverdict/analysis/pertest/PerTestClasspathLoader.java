package dev.coverdict.analysis.pertest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Reads {@code --per-test-classpath <id>=<file>}: one classpath entry per
 * line, the same list-file shape as {@link
 * dev.coverdict.analysis.oracle.ClasspathLoader}'s {@code --classpath} - but
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
 * predicate. coverdict has no Maven/Gradle build-output convention to guess
 * from instead (D-51 rejects build-tool detection outright) - the caller
 * states its classpath explicitly, same as {@code --classpath}.
 */
public final class PerTestClasspathLoader {

    private PerTestClasspathLoader() {
    }

    public static Result load(Path repoRoot, String moduleId, String listFilePath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(repoRoot.resolve(listFilePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return missing(moduleId, listFilePath, "could not be read (" + e.getClass().getSimpleName() + ")");
        }

        List<String> classPathElements = new ArrayList<>();
        List<String> codePaths = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawLine : lines) {
            String entry = rawLine.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            Path resolved = repoRoot.resolve(entry);
            if (!seen.add(resolved.toString())) {
                continue; // same entry named twice - use it once
            }
            classPathElements.add(resolved.toString());
            if (Files.isDirectory(resolved)) {
                codePaths.add(resolved.toString());
            }
        }

        if (codePaths.isEmpty()) {
            return missing(moduleId, listFilePath, "names no compiled-output directory on the classpath");
        }
        return new Result(List.copyOf(classPathElements), List.copyOf(codePaths), List.of());
    }

    private static Result missing(String moduleId, String listFilePath, String detail) {
        return new Result(List.of(), List.of(), List.of(new AnalysisReason("PER_TEST_CLASSPATH_MISSING",
            "Per-test classpath list '" + listFilePath + "' for module '" + moduleId + "' " + detail
                + "; per-test evidence skipped for this module.", null, moduleId)));
    }

    /** {@code warnings} is non-empty exactly when {@code classPathElements}/{@code codePaths} are both empty. */
    public record Result(List<String> classPathElements, List<String> codePaths, List<AnalysisReason> warnings) {
    }
}
