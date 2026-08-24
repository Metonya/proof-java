package dev.coverdict.analysis.oracle;

import java.util.List;

import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.Finding;

/** {@code incompleteReasons} carries one {@code UNPARSEABLE_TEST_SOURCE} per test file that could not be parsed - hard rule 3a, never a silent skip. */
public record OracleScanResult(List<Finding> findings, List<AnalysisReason> incompleteReasons) {
}
