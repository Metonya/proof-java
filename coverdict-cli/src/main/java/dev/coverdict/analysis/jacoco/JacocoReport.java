package dev.coverdict.analysis.jacoco;

import java.util.List;

/**
 * One parsed JaCoCo XML report: its {@code <sourcefile>} entries plus the
 * report-level {@code <counter type="LINE">} totals, kept for the D-04
 * parity check ({@code jacoco-line} must equal this exactly).
 */
public record JacocoReport(
    String name,
    List<SourceFileReport> sourceFiles,
    int totalLineMissed,
    int totalLineCovered
) {
}
