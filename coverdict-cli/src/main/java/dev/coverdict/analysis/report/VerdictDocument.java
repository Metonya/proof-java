package dev.coverdict.analysis.report;

import java.util.List;

import dev.coverdict.analysis.metrics.MetricSet;
import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Everything {@link VerdictJsonWriter} needs. Scoped to what this milestone
 * step produces: {@code diffMode} is always {@code "no-vcs"} here (D-16's
 * base-ref/working-tree modes are the next step - see ROADMAP M1a);
 * {@code changedFiles} and {@code findings} are always empty (no diff
 * support, no L0 oracle rules yet - M1b).
 */
public record VerdictDocument(
    String schemaVersion,
    String toolVersion,
    boolean complete,
    List<AnalysisReason> incompleteReasons,
    int languageLevel,
    String encoding,
    List<String> exclusions,
    List<ModuleInput> modules,
    MetricSet overallMetrics,
    List<AnalysisReason> warnings
) {
}
