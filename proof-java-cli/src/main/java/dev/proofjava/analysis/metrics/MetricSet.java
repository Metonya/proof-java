package dev.proofjava.analysis.metrics;

/**
 * The three modes D-04 requires side by side (schema {@code $defs/metricSet}).
 *
 * <p>The first mode is named after the engine that produced it, because its
 * defining property is that it reproduces that engine's own headline counter
 * exactly: {@code jacoco-line} here, {@code coverage-line} for a verdict
 * written by proof-python (D-99). {@link #engineModeId} carries which one this
 * set actually uses, so a renderer prints the id it was given rather than the
 * one this engine happens to produce - {@code render-html} accepts any
 * document matching the schema (D-78), including a sibling engine's.
 */
public record MetricSet(String engineModeId, Metric engineLine, Metric strictLine, Metric sonarCompatible) {

    /** The id proof-java's own {@code MetricsEngine} produces. */
    public static final String JACOCO_LINE = "jacoco-line";
    /** The id proof-python produces; accepted on read, never written here. */
    public static final String COVERAGE_LINE = "coverage-line";
    public static final String STRICT_LINE = "strict-line";
    public static final String SONAR_COMPATIBLE = "sonar-compatible";

    /** A set produced by this engine, and therefore named after JaCoCo. */
    public MetricSet(Metric jacocoLine, Metric strictLine, Metric sonarCompatible) {
        this(JACOCO_LINE, jacocoLine, strictLine, sonarCompatible);
    }

    /**
     * @deprecated the mode is no longer JaCoCo's by definition; use
     *             {@link #engineLine()} with {@link #engineModeId()}.
     */
    @Deprecated
    public Metric jacocoLine() {
        return engineLine;
    }
}
