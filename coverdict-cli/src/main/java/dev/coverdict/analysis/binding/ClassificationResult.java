package dev.coverdict.analysis.binding;

import java.util.List;

import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;

public record ClassificationResult(List<ChangedFile> changedFiles, List<AnalysisReason> incompleteReasons, List<AnalysisReason> warnings) {
}
