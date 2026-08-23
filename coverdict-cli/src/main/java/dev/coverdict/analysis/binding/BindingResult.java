package dev.coverdict.analysis.binding;

import java.util.List;

import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ResolvedSourceFile;

public record BindingResult(List<ResolvedSourceFile> resolvedFiles, List<AnalysisReason> warnings) {
}
