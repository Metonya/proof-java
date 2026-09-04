package dev.proofjava.analysis.report;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.proofjava.analysis.metrics.Metric;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;

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

        if (!doc.findings().isEmpty()) {
            sb.append(doc.findings().size()).append(" finding(s) (").append(doc.findingsScope()).append("):\n");
            doc.findings().forEach(f -> sb.append("  ").append(f.rule()).append(' ').append(f.confidence())
                .append(' ').append(escape(f.path())).append(':').append(f.startLine()).append(' ').append(escape(f.message())).append('\n'));
        }

        if (!doc.incompleteReasons().isEmpty()) {
            doc.incompleteReasons().forEach(r -> sb.append("  ").append(escape(r.code())).append(": ").append(escape(r.message())).append('\n'));
        }
        if (!doc.warnings().isEmpty()) {
            sb.append(doc.warnings().size()).append(" warning(s):\n");
            doc.warnings().forEach(w -> sb.append("  ").append(escape(w.code())).append(": ").append(escape(w.message())).append('\n'));
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

    /**
     * Replaces C0/DEL/C1 control characters (SECURITY-POLICY.md #4) with a
     * {@code \\uXXXX} escape so a path or message originating from parsed
     * source, a JaCoCo attribute, or a CLI argument can never inject a
     * terminal control sequence or break this renderer's one-line-per-entry
     * structure. {@code \n} in {@code message}/{@code path} would already
     * violate the schema's single-line field contract, but this is the only
     * layer that actually renders to a terminal.
     */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F || (c >= 0x80 && c <= 0x9F)) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
