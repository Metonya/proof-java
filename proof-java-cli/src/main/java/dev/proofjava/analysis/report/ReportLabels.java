package dev.proofjava.analysis.report;

import java.util.Map;

/**
 * D-80: one friendly name + one-sentence explanation for every code that can
 * surface in the HTML report - a rule id
 * ({@link dev.proofjava.analysis.model.RuleIds}), an {@code AnalysisReason}
 * code, a {@link dev.proofjava.analysis.model.Severity}/{@link
 * dev.proofjava.analysis.model.Confidence}/{@link
 * dev.proofjava.analysis.model.Classification} enum constant, a PIT mutant
 * status, or a coverage metric mode ({@code jacoco-line}, ...).
 *
 * <p>The machine-readable code is never replaced, only accompanied (hard
 * rule 5: a metric's canonical id is never dropped in favor of its friendly
 * name) - see {@code docs/GLOSSARY.md} for the prose version of the same
 * mapping. {@link #lookup(String)} on an unknown code returns {@code null}
 * so the caller falls back to the raw code rather than inventing text.
 */
public final class ReportLabels {

    public record Label(String name, String description) {
    }

    private static final Map<String, Label> LABELS = Map.ofEntries(
        // Rule ids (docs/rules/*.md) - L0 static-analysis findings.
        entry("NO_RECOGNIZED_ORACLE", "Test with no assertion",
            "The test contains no recognized assertion, verification or expected exception: the code runs, the test looks green, but nothing is checked."),
        entry("TAUTOLOGICAL_ORACLE", "Self-proving test",
            "The assertion's outcome cannot depend on the code under test: it compares a constant with a constant, or a value with itself, so it always passes."),
        entry("CATCH_ORACLE_WITHOUT_FAIL", "catch block without fail()",
            "Every assertion sits inside the try block and only runs when nothing is thrown; if the code throws, the test still passes."),
        entry("NULL_CHECK_ONLY", "Null check only",
            "Every assertion in the test only checks for non-nullness; the value's content is never verified. A weak test, not a broken one."),
        entry("PSEUDO_TESTED_METHOD", "Pseudo-tested method",
            "A test executes this method, but every mutant generated for it survived: no test notices what the method actually does."),
        entry("SUBSUMED_TEST", "Subsumed test",
            "The set of mutants this test kills is a subset of another test's; the broader test is the suspicious one, not this one."),

        // AnalysisReason codes actually constructed in the codebase.
        entry("CHANGED_FILES_EXCLUDED", "Changed files excluded",
            "Some changed files matched an exclusion pattern in the configuration and were left out of the analysis."),
        entry("CHANGED_JAVA_OUTSIDE_MODULES", "Java file outside every module",
            "A changed Java file does not fall under any defined module's source or test root."),
        entry("CHANGED_LINES_ABSENT_FROM_REPORT", "Changed lines missing from the JaCoCo report",
            "Some lines changed in the diff do not appear in the JaCoCo coverage report at all."),
        entry("CLASSPATH_ENTRY_UNUSABLE", "Unusable classpath entry",
            "One of the supplied classpath entries could not be read or was invalid."),
        entry("CLASSPATH_FILE_UNREADABLE", "Unreadable classpath file",
            "The classpath file could not be opened or read."),
        entry("CLASSPATH_TOO_LARGE", "Classpath too large",
            "The classpath was truncated because it exceeded the limit this analysis can process."),
        entry("FINDINGS_TRUNCATED", "Findings truncated",
            "The finding list was truncated because it exceeded the output limit; the real count may be higher."),
        entry("MISSING_SOURCE_FILE", "Source file not found",
            "The coverage report has data for this file, but the file itself is not under any defined source root."),
        entry("MODULE_WITHOUT_REPORT", "Module without a report",
            "No JaCoCo report was supplied for this module, so its coverage could not be counted."),
        entry("MUTATION_CLASSPATH_MISSING", "Mutation classpath missing",
            "The classpath needed to run mutation analysis was not supplied."),
        entry("MUTATION_EMPTY_EVIDENCE", "Mutation evidence empty",
            "Mutation analysis ran but no mutants were generated or collected."),
        entry("MUTATION_INCONCLUSIVE_STATUS", "Inconclusive mutant status",
            "At least one mutant came back with a status outside the KILLED/SURVIVED/NO_COVERAGE set PIT reports as conclusive."),
        entry("MUTATION_JDK_UNSUPPORTED", "Mutation skipped: JDK too new",
            "The JDK running this analysis is newer than the embedded mutation engine can read class files for, so the evidence was not collected rather than reported as zero findings."),
        entry("MUTATION_NO_CHANGED_TARGETS", "No target for mutation",
            "The changes in the diff could not be bound to any production method to run mutation analysis on."),
        entry("MUTATION_TARGET_NOT_BOUND", "Mutation target not bound",
            "A production method could not be mapped to any class in the mutation report."),
        entry("MUTATION_TARGET_UNRESOLVED", "Mutation target unresolved",
            "A supplied --mutation-target could not be resolved to a source file or a method."),
        entry("MUTATION_TRUNCATED", "Mutation data truncated",
            "The mutant list was truncated because it exceeded the output limit."),
        entry("PER_TEST_CLASSPATH_MISSING", "Per-test classpath missing",
            "The classpath needed to collect per-test coverage was not supplied."),
        entry("PER_TEST_COLLECTION_FAILED", "Per-test collection failed",
            "The per-test coverage collection run failed."),
        entry("PER_TEST_EMPTY_EVIDENCE", "Per-test evidence empty",
            "Per-test coverage collection ran but produced no records."),
        entry("PER_TEST_JDK_UNSUPPORTED", "Per-test evidence skipped: JDK too new",
            "The JDK running this analysis is newer than the embedded coverage engine can read class files for, so the evidence was not collected rather than reported as empty."),
        entry("PER_TEST_NO_CHANGED_TARGETS", "No target for per-test evidence",
            "The changes in the diff could not be bound to any production method to collect per-test coverage for."),
        entry("PER_TEST_TARGET_NOT_BOUND", "Per-test target not bound",
            "A production method could not be mapped to any record in the per-test coverage data."),
        entry("PER_TEST_TARGET_UNRESOLVED", "Per-test target unresolved",
            "A supplied --per-test-target could not be resolved to a source file and was skipped."),
        entry("PER_TEST_TRUNCATED", "Per-test evidence truncated",
            "The record list was truncated because it exceeded the output limit."),
        entry("REPORT_MISSING_CHANGED_FILE", "Changed file missing from the report",
            "The JaCoCo report has no record at all for a changed file."),
        entry("SUPPRESSED_FINDINGS", "Suppressed findings",
            "Some findings were kept out of the report by suppressions in the configuration."),
        entry("UNPARSEABLE_TEST_SOURCE", "Unparseable test source",
            "A test file could not be parsed into an AST, so the tests in it could not be included in the L0 analysis."),
        entry("UNTRACKED_JAVA_FILE", "Untracked Java file",
            "A Java file that git does not track was ignored in the diff analysis."),
        entry("UNTRACKED_NON_JAVA_FILE", "Untracked file",
            "A non-Java file that git does not track was ignored in the diff analysis."),

        // Severity
        entry("INFO", "Info", "Not a code defect; it points at something weak or improvable."),
        entry("WARNING", "Warning", "The test's evidential value is questionable; worth reviewing."),

        // Confidence
        entry("HIGH", "High confidence", "The finding rests on a clear match of the pattern."),
        entry("MEDIUM", "Medium confidence", "The finding is likely but not certain; checking it by hand may be worthwhile."),
        entry("LOW", "Low confidence", "The finding rests on a weak signal; a deletion is never recommended at this level."),
        entry("INCONCLUSIVE", "Inconclusive", "The evidence is not enough to reach a conclusion."),

        // Classification (ChangedFile)
        entry("mapped", "Mapped", "The changed file was matched to a source root and to coverage data."),
        entry("excluded", "Excluded", "The changed file matched an exclusion pattern in the configuration."),
        entry("non-executable", "Non-executable", "The file has no executable lines to cover (an interface or constants only, for example)."),
        entry("unsupported", "Unsupported", "This release does not analyze this file's type or structure."),
        entry("unknown", "Unknown", "The file did not match any defined source or test root."),

        // Mutant statuses (PIT)
        entry("KILLED", "Killed", "At least one test noticed this mutation and failed."),
        entry("SURVIVED", "Survived", "The code was broken and no test noticed; the tests still passed."),
        entry("NO_COVERAGE", "Never executed", "No test runs this line, so the mutant was never tried."),
        entry("TIMED_OUT", "Timed out", "The mutated code timed out while running - usually a sign of an infinite loop."),
        entry("NON_VIABLE", "Non-viable mutant", "The mutated code did not compile or could not start a JVM, so it could not be tested."),
        entry("MEMORY_ERROR", "Memory error", "A memory error occurred while the mutated code was running."),
        entry("RUN_ERROR", "Run error", "An unexpected error occurred while the mutant was running."),
        entry("STARTED", "Started", "The mutant started running but the process ended before a result was recorded."),
        entry("NOT_STARTED", "Not started", "The mutant never started running."),

        // Coverage metric modes (hard rule 5: id kept, name is additive)
        entry("jacoco-line", "Line coverage (JaCoCo)", "Matches JaCoCo's own LINE counter exactly: the share of lines where at least one instruction ran."),
        entry("coverage-line", "Line coverage (coverage.py)", "Matches coverage.py's own statement percentage exactly: the share of statements that ran. proof-python's counterpart to jacoco-line."),
        entry("strict-line", "Strict line coverage", "A line counts as covered only if every instruction on it ran, and no branch on it was missed; stricter than the engine's own line counter."),
        entry("sonar-compatible", "Line + branch coverage (Sonar)", "Matches SonarQube's coverage percentage within 0.1 points: line and branch coverage combined.")
    );

    private static Map.Entry<String, Label> entry(String code, String name, String description) {
        return Map.entry(code, new Label(name, description));
    }

    public static Label lookup(String code) {
        return code == null ? null : LABELS.get(code);
    }

    public static Map<String, Label> all() {
        return LABELS;
    }

    private ReportLabels() {
    }
}
