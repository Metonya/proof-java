package dev.coverdict.analysis.report;

import dev.coverdict.analysis.metrics.Metric;

/** Human-readable rendering of a {@link VerdictDocument} (hard rule 7: same document, two readers). */
public final class TextRenderer {

    private TextRenderer() {
    }

    public static String render(VerdictDocument doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.complete()) {
            sb.append("coverdict: analysis complete (no-vcs, overall coverage only)\n");
            sb.append(metricLine("jacoco-line", doc.overallMetrics().jacocoLine()));
            sb.append(metricLine("strict-line", doc.overallMetrics().strictLine()));
            sb.append(metricLine("sonar-compatible", doc.overallMetrics().sonarCompatible()));
        } else {
            sb.append("coverdict: analysis incomplete\n");
            doc.incompleteReasons().forEach(r -> sb.append("  ").append(r.code()).append(": ").append(r.message()).append('\n'));
        }
        if (!doc.warnings().isEmpty()) {
            sb.append(doc.warnings().size()).append(" warning(s):\n");
            doc.warnings().forEach(w -> sb.append("  ").append(w.code()).append(": ").append(w.message()).append('\n'));
        }
        return sb.toString();
    }

    private static String metricLine(String name, Metric metric) {
        String percent = metric.percent() == null ? "n/a" : metric.percent() + "%";
        return "  %-17s %6s (%d/%d)%n".formatted(name, percent, metric.numerator(), metric.denominator());
    }
}
