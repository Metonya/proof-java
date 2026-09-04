package dev.proofjava.analysis.binding;

import java.util.List;

import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.ResolvedSourceFile;

/**
 * {@code newCodeDataset} is the same lines behind every {@code mapped}
 * {@link ChangedFile}'s {@code newLines}/{@code coveredNewLines} - one
 * {@link ResolvedSourceFile} per mapped path, its {@code lines()} already
 * restricted to changed-and-report-known lines - ready to feed straight into
 * {@link dev.proofjava.analysis.metrics.MetricsEngine#compute} for the
 * aggregate {@code coverage.newCode} {@code MetricSet} (hard rule 4: this is
 * the SAME intersection that produced the per-file numbers, not a second,
 * possibly-diverging computation of it).
 */
public record ClassificationResult(
    List<ChangedFile> changedFiles,
    List<ResolvedSourceFile> newCodeDataset,
    List<AnalysisReason> incompleteReasons,
    List<AnalysisReason> warnings
) {
}
