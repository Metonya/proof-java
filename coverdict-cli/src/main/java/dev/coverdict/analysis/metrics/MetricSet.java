package dev.coverdict.analysis.metrics;

/** The three modes D-04 requires side by side (schema {@code $defs/metricSet}). */
public record MetricSet(Metric jacocoLine, Metric strictLine, Metric sonarCompatible) {
}
