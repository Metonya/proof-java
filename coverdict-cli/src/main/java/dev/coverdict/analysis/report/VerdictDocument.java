package dev.coverdict.analysis.report;

import java.util.List;

import dev.coverdict.analysis.metrics.MetricSet;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;
import dev.coverdict.analysis.pertest.PerTestModuleEvidence;
import dev.coverdict.analysis.vcs.VcsIdentity;

/**
 * Everything {@link VerdictJsonWriter} needs. {@code identity} is {@code
 * null} exactly when {@code diffMode} is {@code "no-vcs"} - in every other
 * case it carries at least {@code head}/{@code dirty}, and {@code baseRef}/
 * {@code base}/{@code mergeBase} besides in base-ref mode (D-16).
 * {@code findingsScope} is {@code "all"} or {@code "changed"} (M1b K1);
 * {@code "changed"} is only reachable together with a diff mode.
 * {@code perTest} is {@code null} unless {@code --per-test-report} was set
 * (D-46/D-55: opt-in L2 evidence, never required for a complete verdict).
 * {@code mutation} is {@code null} unless {@code --mutation-report} was set
 * (D-46/D-56: opt-in L3 evidence, same contract).
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
    String findingsScope,
    VcsIdentity identity,
    MetricSet overallMetrics,
    NewCodeCoverage newCode,
    List<ChangedFile> changedFiles,
    List<Finding> findings,
    List<AnalysisReason> warnings,
    List<PerTestModuleEvidence> perTest,
    List<MutationModuleEvidence> mutation
) {
    /** Pre-D-55 shape, {@code perTest}/{@code mutation} always absent - kept so existing call sites and tests need no change. */
    public VerdictDocument(String schemaVersion, String toolVersion, boolean complete,
                            List<AnalysisReason> incompleteReasons, int languageLevel, String encoding,
                            List<String> exclusions, List<ModuleInput> modules, String diffMode, String findingsScope,
                            VcsIdentity identity, MetricSet overallMetrics, NewCodeCoverage newCode,
                            List<ChangedFile> changedFiles, List<Finding> findings, List<AnalysisReason> warnings) {
        this(schemaVersion, toolVersion, complete, incompleteReasons, languageLevel, encoding, exclusions, modules,
            diffMode, findingsScope, identity, overallMetrics, newCode, changedFiles, findings, warnings, null, null);
    }

    /** Post-D-55, pre-D-56 shape, {@code mutation} always absent - kept so existing call sites and tests need no change. */
    public VerdictDocument(String schemaVersion, String toolVersion, boolean complete,
                            List<AnalysisReason> incompleteReasons, int languageLevel, String encoding,
                            List<String> exclusions, List<ModuleInput> modules, String diffMode, String findingsScope,
                            VcsIdentity identity, MetricSet overallMetrics, NewCodeCoverage newCode,
                            List<ChangedFile> changedFiles, List<Finding> findings, List<AnalysisReason> warnings,
                            List<PerTestModuleEvidence> perTest) {
        this(schemaVersion, toolVersion, complete, incompleteReasons, languageLevel, encoding, exclusions, modules,
            diffMode, findingsScope, identity, overallMetrics, newCode, changedFiles, findings, warnings, perTest, null);
    }
}
