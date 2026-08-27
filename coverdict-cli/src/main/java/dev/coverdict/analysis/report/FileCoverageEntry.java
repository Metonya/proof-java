package dev.coverdict.analysis.report;

import java.util.List;

import dev.coverdict.analysis.jacoco.LineCoverage;
import dev.coverdict.analysis.metrics.MetricSet;

/**
 * Schema {@code fileCoverage.files[]}: one repo-relative source file's own
 * {@link MetricSet}, computed by {@link dev.coverdict.analysis.metrics.MetricsEngine}
 * over that file's own filtered lines - the same engine and the same filtered
 * dataset {@code coverage.overall} is computed from (hard rule 4), never a
 * client-side recomputation (Plan.md Faz 1: {@code Metric.percent()}'s
 * {@code BigDecimal} half-up rounding is not float-safely reproducible in
 * TypeScript). {@code lines} are written as compact tuples matching {@link
 * LineCoverage}'s own field order - see {@code VerdictJsonWriter.writeFileCoverageEntry}.
 */
public record FileCoverageEntry(String module, String path, MetricSet metrics, List<LineCoverage> lines) {
}
