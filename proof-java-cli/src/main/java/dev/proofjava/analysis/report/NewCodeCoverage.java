package dev.proofjava.analysis.report;

import dev.proofjava.analysis.metrics.MetricSet;

/**
 * Schema {@code coverage.newCode}'s {@code oneOf}: either a real {@link
 * MetricSet}, or an unavailable status ({@code unavailable_no_vcs} /
 * {@code unavailable_incomplete}) - never both, never neither. Exactly one
 * of {@code metrics}/{@code unavailableStatus} is non-null; use the factory
 * methods rather than the canonical constructor to keep that true.
 */
public record NewCodeCoverage(MetricSet metrics, String unavailableStatus) {

    public static NewCodeCoverage available(MetricSet metrics) {
        return new NewCodeCoverage(metrics, null);
    }

    public static NewCodeCoverage unavailable(String status) {
        return new NewCodeCoverage(null, status);
    }
}
