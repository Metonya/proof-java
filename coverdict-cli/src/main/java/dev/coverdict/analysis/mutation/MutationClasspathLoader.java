package dev.coverdict.analysis.mutation;

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
 * Reads {@code --mutation-classpath <id>=<file>}: one classpath entry per
 * line, the same list-file shape as {@link
 * dev.coverdict.analysis.pertest.PerTestClasspathLoader}'s {@code
 * --per-test-classpath} - a separate loader rather than a shared one so
 * this evidence layer's own warning code ({@code MUTATION_CLASSPATH_MISSING})
 * and message never get confused with L2's.
 *
 * <p>Entries that are directories on disk (a module's compiled output) are
 * also collected as {@code codePaths} - {@code ReportOptions.setCodePaths}
 * distinguishes "code under test" from dependency jars on the same
 * classpath, same as the L2 loader.
 */
public final class MutationClasspathLoader {

    private MutationClasspathLoader() {
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
            resolveLine(repoRoot, rawLine, seen, classPathElements, codePaths);
        }

        if (codePaths.isEmpty()) {
            return missing(moduleId, listFilePath, "names no compiled-output directory on the classpath");
        }
        return new Result(List.copyOf(classPathElements), List.copyOf(codePaths), List.of());
    }

    /** One line of a classpath list file (SonarQube java:S135 - kept to a single {@code continue}-free shape). */
    private static void resolveLine(Path repoRoot, String rawLine, Set<String> seen, List<String> classPathElements,
                                     List<String> codePaths) {
        String entry = rawLine.trim();
        if (entry.isEmpty() || entry.startsWith("#")) {
            return;
        }
        Path resolved = repoRoot.resolve(entry);
        if (!seen.add(resolved.toString())) {
            return; // same entry named twice - use it once
        }
        classPathElements.add(resolved.toString());
        if (Files.isDirectory(resolved)) {
            codePaths.add(resolved.toString());
        }
    }

    private static Result missing(String moduleId, String listFilePath, String detail) {
        return new Result(List.of(), List.of(), List.of(new AnalysisReason("MUTATION_CLASSPATH_MISSING",
            "Mutation classpath list '" + listFilePath + "' for module '" + moduleId + "' " + detail
                + "; mutation evidence skipped for this module.", null, moduleId)));
    }

    /** {@code warnings} is non-empty exactly when {@code classPathElements}/{@code codePaths} are both empty. */
    public record Result(List<String> classPathElements, List<String> codePaths, List<AnalysisReason> warnings) {
    }
}
