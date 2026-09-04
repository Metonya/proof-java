package dev.proofjava.analysis.binding;

import java.util.List;

import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ResolvedSourceFile;

public record BindingResult(List<ResolvedSourceFile> resolvedFiles, List<AnalysisReason> warnings) {
}
