package dev.coverdict.analysis.jacoco;

import java.util.List;

/**
 * One {@code <sourcefile>} within a JaCoCo XML {@code <package>}, plus the
 * package path it belongs to. {@code packageName} uses forward slashes as
 * JaCoCo itself does (e.g. {@code "com/example"}, or {@code ""} for the
 * default package) - this is not yet a repo-relative path; module/source-root
 * resolution (next pipeline stage) produces that.
 */
public record SourceFileReport(
    String packageName,
    String fileName,
    List<LineCoverage> lines,
    int reportedLineMissed,
    int reportedLineCovered
) {

    /** e.g. "com/example/Calc.java", or "Calc.java" for the default package. */
    public String packageQualifiedPath() {
        return packageName.isEmpty() ? fileName : packageName + "/" + fileName;
    }
}
