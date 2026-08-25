package dev.coverdict.analysis.subprocess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Shared parsing for a PIT classpath list file: one entry per line, `#`
 * comments and blank lines skipped, directory entries also collected as
 * {@code codePaths} (PIT's "code under test", distinct from dependency
 * jars on the same classpath). Both {@code --per-test-classpath} and
 * {@code --mutation-classpath} use this identical shape but keep their own
 * thin loader wrapper (SonarQube java:S1192 precedent - a distinct warning
 * code/message per evidence layer) - {@link #toWarning} parameterizes that
 * last difference too, so the wrapper is pure delegation with nothing left
 * to duplicate (SonarQube CPD had still flagged the two wrappers'
 * identically-shaped load/missing methods even with different string
 * literals inside).
 *
 * @param problem null on success; otherwise a detail phrase ("could not be
 *                read (...)", "names no compiled-output directory on the
 *                classpath") {@link #toWarning} embeds verbatim -
 *                {@code classPathElements}/{@code codePaths} are both empty
 *                whenever this is non-null.
 */
public record ClasspathListFile(List<String> classPathElements, List<String> codePaths, String problem) {

    public static ClasspathListFile load(Path repoRoot, String listFilePath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(repoRoot.resolve(listFilePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ClasspathListFile(List.of(), List.of(), "could not be read (" + e.getClass().getSimpleName() + ")");
        }

        List<String> classPathElements = new ArrayList<>();
        List<String> codePaths = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawLine : lines) {
            resolveLine(repoRoot, rawLine, seen, classPathElements, codePaths);
        }

        if (codePaths.isEmpty()) {
            return new ClasspathListFile(List.of(), List.of(), "names no compiled-output directory on the classpath");
        }
        return new ClasspathListFile(List.copyOf(classPathElements), List.copyOf(codePaths), null);
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

    public boolean ok() {
        return problem == null;
    }

    /**
     * @throws IllegalStateException if called on a successful load - {@link #problem} is null then, there is nothing to warn about.
     */
    public AnalysisReason toWarning(String warningCode, String evidenceLabel, String moduleId, String listFilePath) {
        if (ok()) {
            throw new IllegalStateException("toWarning() called on a successful ClasspathListFile load");
        }
        return new AnalysisReason(warningCode,
            evidenceLabel + " classpath list '" + listFilePath + "' for module '" + moduleId + "' " + problem
                + "; " + evidenceLabel.toLowerCase(Locale.ROOT) + " evidence skipped for this module.", null, moduleId);
    }
}
