package dev.coverdict.analysis.report;

/** Schema {@code inputs.modules[].reports[]}: a bound JaCoCo XML path plus its freshness label. */
public record ReportInput(String path, String freshness) {
}
