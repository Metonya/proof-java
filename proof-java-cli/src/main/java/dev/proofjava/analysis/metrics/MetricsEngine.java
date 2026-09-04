package dev.proofjava.analysis.metrics;

import java.util.List;

import dev.proofjava.analysis.jacoco.LineCoverage;
import dev.proofjava.analysis.model.ResolvedSourceFile;

/**
 * Computes all three metric modes from one filtered dataset (hard rule 4):
 * no number here is ever read back out of a raw JaCoCo counter - every
 * numerator and denominator is summed from the same {@code lines} that
 * {@link ExclusionFilter} already filtered. The same method serves both
 * scopes the schema defines: {@code coverage.overall} (the full filtered
 * dataset) and {@code coverage.newCode} (the same dataset shape, but each
 * file's {@code lines()} already restricted to changed-and-report-known
 * lines by {@link dev.proofjava.analysis.binding.ChangedFileClassifier}) -
 * one formula, never two.
 */
public final class MetricsEngine {

    private MetricsEngine() {
    }

    public static MetricSet compute(List<ResolvedSourceFile> filteredFiles) {
        int executableLines = 0;
        int coveredLines = 0;
        int fullyCoveredLines = 0;
        int coveredBranches = 0;
        int missedBranches = 0;

        for (ResolvedSourceFile file : filteredFiles) {
            for (LineCoverage line : file.lines()) {
                executableLines++;
                if (line.isCovered()) {
                    coveredLines++;
                }
                if (line.isFullyCovered()) {
                    fullyCoveredLines++;
                }
                coveredBranches += line.coveredBranches();
                missedBranches += line.missedBranches();
            }
        }

        // jacoco-line (D-04): any instruction on the line ran.
        Metric jacocoLine = Metric.of("coveredLines", coveredLines, "executableLines", executableLines);

        // strict-line (D-19): ci>0 && mi==0. Branch completeness is
        // deliberately NOT folded in - that was the prototype's mistake.
        Metric strictLine = Metric.of("fullyCoveredLines", fullyCoveredLines, "executableLines", executableLines);

        // sonar-compatible (D-04): (cb + LC) / (cb + mb + EL).
        Metric sonarCompatible = Metric.of(
            "coveredBranchesPlusCoveredLines", coveredBranches + coveredLines,
            "branchesPlusExecutableLines", coveredBranches + missedBranches + executableLines);

        return new MetricSet(jacocoLine, strictLine, sonarCompatible);
    }
}
