package dev.proofjava.analysis.model;

import java.util.List;

import dev.proofjava.analysis.jacoco.LineCoverage;

/**
 * One source file's coverage data after module binding: a repo-relative,
 * forward-slash path (schema {@code changedFiles[].path} shape) instead of
 * the raw JaCoCo {@code package/sourcefile} pair.
 */
public record ResolvedSourceFile(
    String moduleId,
    String repoRelativePath,
    List<LineCoverage> lines,
    int reportedLineMissed,
    int reportedLineCovered,
    boolean foundOnDisk
) {
}
