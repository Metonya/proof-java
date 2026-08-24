package dev.coverdict.analysis.report;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.coverdict.analysis.metrics.Metric;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;

/** Human-readable rendering of a {@link VerdictDocument} (hard rule 7: same document, two readers). */
public final class TextRenderer {

    private TextRenderer() {
    }

    public static String render(VerdictDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("coverdict: analysis ").append(doc.complete() ? "complete" : "incomplete")
            .append(" (").append(doc.diffMode()).append(")\n");

        sb.append(metricLine("jacoco-line", doc.overallMetrics().jacocoLine()));
        sb.append(metricLine("strict-line", doc.overallMetrics().strictLine()));
        sb.append(metricLine("sonar-compatible", doc.overallMetrics().sonarCompatible()));

        if (doc.newCode().metrics() != null) {
            sb.append("  new code:\n");
            sb.append(metricLine("jacoco-line", doc.newCode().metrics().jacocoLine()));
            sb.append(metricLine("strict-line", doc.newCode().metrics().strictLine()));
            sb.append(metricLine("sonar-compatible", doc.newCode().metrics().sonarCompatible()));
        } else {
            sb.append("  new code: ").append(doc.newCode().unavailableStatus()).append('\n');
        }

        if (!doc.changedFiles().isEmpty()) {
            sb.append(doc.changedFiles().size()).append(" changed file(s): ").append(classificationSummary(doc)).append('\n');
        }

        if (!doc.incompleteReasons().isEmpty()) {
            doc.incompleteReasons().forEach(r -> sb.append("  ").append(r.code()).append(": ").append(r.message()).append('\n'));
        }
        if (!doc.warnings().isEmpty()) {
            sb.append(doc.warnings().size()).append(" warning(s):\n");
            doc.warnings().forEach(w -> sb.append("  ").append(w.code()).append(": ").append(w.message()).append('\n'));
        }
        return sb.toString();
    }

    private static String classificationSummary(VerdictDocument doc) {
        Map<Classification, Long> counts = new LinkedHashMap<>();
        for (ChangedFile file : doc.changedFiles()) {
            counts.merge(file.classification(), 1L, Long::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().schemaValue()))
            .forEach(e -> {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(e.getValue()).append(' ').append(e.getKey().schemaValue());
            });
        return sb.toString();
    }

    private static String metricLine(String name, Metric metric) {
        String percent = metric.percent() == null ? "n/a" : metric.percent() + "%";
        return "  %-17s %6s (%d/%d)%n".formatted(name, percent, metric.numerator(), metric.denominator());
    }
}
