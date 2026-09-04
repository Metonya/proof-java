package dev.proofjava.analysis.oracle;

import java.util.List;

import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.Finding;

/**
 * @param incompleteReasons one {@code UNPARSEABLE_TEST_SOURCE} per test file
 *                          that could not be parsed - hard rule 3a, never a
 *                          silent skip
 * @param warnings          non-fatal notes the verdict must still surface,
 *                          currently {@code SUPPRESSED_FINDINGS} (D-42): a
 *                          suppressed finding is hidden from the list but
 *                          never from the reader
 */
public record OracleScanResult(List<Finding> findings, List<AnalysisReason> incompleteReasons,
                                List<AnalysisReason> warnings) {

    public OracleScanResult(List<Finding> findings, List<AnalysisReason> incompleteReasons) {
        this(findings, incompleteReasons, List.of());
    }
}
