package dev.coverdict.analysis.report;

import java.util.List;

import dev.coverdict.analysis.metrics.MetricSet;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.vcs.VcsIdentity;

/**
 * Everything {@link VerdictJsonWriter} needs. {@code findings} is still
 * always empty (no L0 oracle rules yet - M1b). {@code identity} is {@code
 * null} exactly when {@code diffMode} is {@code "no-vcs"} - in every other
 * case it carries at least {@code head}/{@code dirty}, and {@code baseRef}/
 * {@code base}/{@code mergeBase} besides in base-ref mode (D-16).
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
    String diffMode,
    VcsIdentity identity,
    MetricSet overallMetrics,
    NewCodeCoverage newCode,
    List<ChangedFile> changedFiles,
    List<AnalysisReason> warnings
) {
}
