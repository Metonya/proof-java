package dev.proofjava.analysis.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.Confidence;
import dev.proofjava.analysis.model.RuleIds;
import dev.proofjava.analysis.model.Severity;

/**
 * D-80: every code the HTML report can show next to a friendly name must
 * actually have one in {@link ReportLabels} - an unlabeled code falls back
 * to its raw form in the renderer, which is a silent regression to the old
 * report, not a crash, so it needs its own test to catch.
 */
class ReportLabelsTest {

    private static final List<String> MUTANT_STATUSES = List.of(
        "KILLED", "SURVIVED", "NO_COVERAGE", "TIMED_OUT", "NON_VIABLE", "MEMORY_ERROR", "RUN_ERROR", "STARTED",
        "NOT_STARTED");

    private static final List<String> METRIC_MODES = List.of("jacoco-line", "strict-line", "sonar-compatible");

    /** The exact set of {@code AnalysisReason} codes this codebase actually constructs (grep-verified against `new AnalysisReason(`), not the larger set of string constants that only ever appear inside an exception message or the doctor command. */
    private static final List<String> ANALYSIS_REASON_CODES = List.of(
        "CHANGED_FILES_EXCLUDED", "CHANGED_JAVA_OUTSIDE_MODULES", "CHANGED_LINES_ABSENT_FROM_REPORT",
        "CLASSPATH_ENTRY_UNUSABLE", "CLASSPATH_FILE_UNREADABLE", "CLASSPATH_TOO_LARGE", "FINDINGS_TRUNCATED",
        "MISSING_SOURCE_FILE", "MODULE_WITHOUT_REPORT", "MUTATION_CLASSPATH_MISSING", "MUTATION_EMPTY_EVIDENCE",
        "MUTATION_INCONCLUSIVE_STATUS", "MUTATION_JDK_UNSUPPORTED", "MUTATION_NO_CHANGED_TARGETS",
        "MUTATION_TARGET_NOT_BOUND",
        "MUTATION_TARGET_UNRESOLVED", "MUTATION_TRUNCATED", "PER_TEST_CLASSPATH_MISSING",
        "PER_TEST_COLLECTION_FAILED", "PER_TEST_EMPTY_EVIDENCE", "PER_TEST_JDK_UNSUPPORTED",
        "PER_TEST_NO_CHANGED_TARGETS",
        "PER_TEST_TARGET_NOT_BOUND", "PER_TEST_TARGET_UNRESOLVED", "PER_TEST_TRUNCATED",
        "REPORT_MISSING_CHANGED_FILE", "SUPPRESSED_FINDINGS", "UNPARSEABLE_TEST_SOURCE", "UNTRACKED_JAVA_FILE",
        "UNTRACKED_NON_JAVA_FILE");

    @Test
    void everyRuleIdHasALabel() {
        for (String rule : RuleIds.ALL) {
            assertLabeled(rule);
        }
    }

    @Test
    void everyAnalysisReasonCodeHasALabel() {
        for (String code : ANALYSIS_REASON_CODES) {
            assertLabeled(code);
        }
    }

    @Test
    void everySeverityHasALabel() {
        for (Severity s : Severity.values()) {
            assertLabeled(s.name());
        }
    }

    @Test
    void everyConfidenceHasALabel() {
        for (Confidence c : Confidence.values()) {
            assertLabeled(c.name());
        }
    }

    @Test
    void everyClassificationHasALabel() {
        for (Classification c : Classification.values()) {
            assertLabeled(c.schemaValue());
        }
    }

    @Test
    void everyMutantStatusHasALabel() {
        for (String status : MUTANT_STATUSES) {
            assertLabeled(status);
        }
    }

    @Test
    void everyMetricModeHasALabel() {
        for (String mode : METRIC_MODES) {
            assertLabeled(mode);
        }
    }

    /** Every {@code String} constant declared directly on {@link RuleIds} (not just the ones {@link RuleIds#ALL} happens to list) must be labeled too - this is what would have caught a rule id added to the class but forgotten in {@code ALL}. */
    @Test
    void ruleIdsAllMatchesTheClassConstantsExactly() throws IllegalAccessException {
        Set<String> declared = new java.util.HashSet<>();
        for (Field f : RuleIds.class.getDeclaredFields()) {
            if (f.getType() == String.class && Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
                declared.add((String) f.get(null));
            }
        }
        assertFalse(declared.isEmpty(), "expected at least one rule id constant");
        assertTrue(RuleIds.ALL.containsAll(declared), "RuleIds.ALL is missing: " + declared);
    }

    @Test
    void unknownCodeReturnsNull() {
        assertNotNull(ReportLabels.lookup("NO_RECOGNIZED_ORACLE"));
        List<String> unknown = new ArrayList<>();
        unknown.add(null);
        assertTrue(ReportLabels.lookup(unknown.get(0)) == null);
        assertTrue(ReportLabels.lookup("THIS_CODE_DOES_NOT_EXIST") == null);
    }

    private void assertLabeled(String code) {
        ReportLabels.Label label = ReportLabels.lookup(code);
        assertNotNull(label, "missing ReportLabels entry for: " + code);
        assertFalse(label.name().isBlank(), "blank label name for: " + code);
        assertFalse(label.description().isBlank(), "blank label description for: " + code);
    }
}
