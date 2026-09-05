package dev.proofjava.analysis.oracle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.TypeSolver;

import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.Fingerprint;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * Runs the four L0 rules (docs/rules/) over every test source file a module
 * declares (K1: {@code --findings-scope}), one {@link OracleRecognizer}
 * traversal per test method feeding all four rule evaluations - the
 * traversal, not the rule count, is the expensive part, so it happens once.
 *
 * <p>{@link CatchOracleWithoutFailRule} and {@link NoRecognizedOracleRule}
 * are mutually exclusive per method (docs/rules/README.md /
 * NO_RECOGNIZED_ORACLE.md: "only one of the two fires, this one" - the more
 * specific catch-shape wins).
 */
public final class OracleRuleEngine {

    /** Lowest Java language level the embedded parser is configured for. */
    public static final int MIN_LANGUAGE_LEVEL = 8;

    /**
     * Highest Java language level the embedded parser knows - its
     * {@code LanguageLevel} enum ends there. A higher level cannot be honored,
     * and quietly parsing newer syntax at an older level would report
     * {@code UNPARSEABLE_TEST_SOURCE} against the user's tests rather than
     * naming this tool's own limit (hard rule 3a), so callers reject it up front.
     */
    public static final int MAX_LANGUAGE_LEVEL = 21;

    static {
        // JavaParser's own PrimitiveType resolution (javaparser-core) does an
        // internal default-locale case conversion; under a Turkish default
        // locale "INT".toLowerCase() yields "ınt" (dotless i), which fails
        // ResolvedPrimitiveType.byName and silently degrades every call whose
        // signature touches a primitive parameter from SOLVED to UNRESOLVED -
        // not a crash, a wrong (falsely lower-confidence) analysis result.
        // Reproduced exactly on this project's own dev machine (tr_TR
        // default). A deterministic analyzer must not depend on the host
        // locale at all (the same reasoning VerdictJsonWriter's BigDecimal
        // usage documents for percentages) - forced once, for the whole
        // process, the first time this class loads.
        java.util.Locale.setDefault(java.util.Locale.ROOT);
    }

    private OracleRuleEngine() {
    }

    public static OracleScanResult scan(Path repoRoot, List<ModuleDefinition> modules, int languageLevel,
                                         String encoding, Set<String> changedPathsOrNull) {
        return scan(repoRoot, modules, languageLevel, encoding, changedPathsOrNull, OracleScanOptions.defaults());
    }

    /**
     * @param options classpath solvers, configured custom oracles and
     *                suppressions, and the findings cap - see
     *                {@link OracleScanOptions}. The cap is checked before each
     *                FILE, not each finding: a file's findings are never split
     *                across the boundary, so the output stays explainable (a
     *                file's result is whole or absent, never partial) at the
     *                cost of a truncated run's total possibly exceeding the cap
     *                slightly. Intentional, not a bug (M1c-1 D-29).
     */
    public static OracleScanResult scan(Path repoRoot, List<ModuleDefinition> modules, int languageLevel, String encoding,
                                  Set<String> changedPathsOrNull, OracleScanOptions options) {
        List<TypeSolver> extraTypeSolvers = options.extraTypeSolvers();
        int findingsCap = options.findingsCap();
        CustomOracles customOracles = CustomOracles.of(options.customOracles());
        List<TestSourceFile> files = TestSourceScanner.scan(repoRoot, modules, changedPathsOrNull);
        JavaSourceParser parser = new JavaSourceParser(repoRoot, modules, languageLevel, encoding, extraTypeSolvers);

        List<Finding> findings = new ArrayList<>();
        List<AnalysisReason> incompleteReasons = new ArrayList<>();
        boolean truncated = false;

        for (TestSourceFile file : files) {
            if (findings.size() >= findingsCap) {
                truncated = true;
                break;
            }
            scanOneFile(file, parser, customOracles, findings, incompleteReasons);
        }
        if (truncated) {
            incompleteReasons.add(new AnalysisReason("FINDINGS_TRUNCATED",
                "Analysis stopped after " + findingsCap + " findings (SECURITY-POLICY.md #2); not every test source was scanned."));
        }

        findings.sort(Comparator.comparing(Finding::path).thenComparingInt(Finding::startLine)
            .thenComparing(Finding::rule).thenComparing(Finding::fingerprint));

        // Applied after sorting and after the cap: suppression hides findings
        // from the reader's list, it does not change which files were scanned
        // or how the truncation boundary fell.
        SuppressionFilter.Result suppression = SuppressionFilter.apply(findings, options.suppressions());
        List<AnalysisReason> warnings = new ArrayList<>();
        if (suppression.suppressedCount() > 0) {
            warnings.add(new AnalysisReason("SUPPRESSED_FINDINGS",
                suppression.suppressedCount() + " finding(s) matched a configured suppression and are not listed.",
                null, null, suppression.suppressedCount()));
        }
        return new OracleScanResult(suppression.findings(), incompleteReasons, warnings);
    }

    private static void scanOneFile(TestSourceFile file, JavaSourceParser parser, CustomOracles customOracles,
                                     List<Finding> findings, List<AnalysisReason> incompleteReasons) {
        Optional<CompilationUnit> cu = parser.parse(file.absolutePath());
        if (cu.isEmpty()) {
            incompleteReasons.add(new AnalysisReason("UNPARSEABLE_TEST_SOURCE",
                "Test source '" + file.repoRelativePath() + "' could not be parsed at the configured language "
                    + "level; skipped, run continues.", file.repoRelativePath(), file.moduleId()));
            return;
        }
        findings.addAll(scanFile(file, cu.get(), customOracles));
    }

    private static List<Finding> scanFile(TestSourceFile file, CompilationUnit cu, CustomOracles customOracles) {
        OracleRecognizer recognizer = new OracleRecognizer(cu, customOracles);
        List<Finding> fileFindings = new ArrayList<>();
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!TestMethods.isTestMethod(method)) {
                continue;
            }
            TraversalResult traversal = recognizer.traverse(method);

            Optional<RuleFinding> catchFinding = CatchOracleWithoutFailRule.evaluate(method, traversal);
            Optional<RuleFinding> noOracleFinding = catchFinding.isPresent()
                ? Optional.empty()
                : NoRecognizedOracleRule.evaluate(method, traversal);

            addIfPresent(fileFindings, file, method, CatchOracleWithoutFailRule.RULE_ID,
                CatchOracleWithoutFailRule.SEVERITY, CatchOracleWithoutFailRule.SUGGESTED_ACTION, catchFinding);
            addIfPresent(fileFindings, file, method, NoRecognizedOracleRule.RULE_ID,
                NoRecognizedOracleRule.SEVERITY, NoRecognizedOracleRule.SUGGESTED_ACTION, noOracleFinding);
            addIfPresent(fileFindings, file, method, TautologicalOracleRule.RULE_ID,
                TautologicalOracleRule.SEVERITY, TautologicalOracleRule.SUGGESTED_ACTION,
                TautologicalOracleRule.evaluate(method, traversal));
            addIfPresent(fileFindings, file, method, NullCheckOnlyRule.RULE_ID,
                NullCheckOnlyRule.SEVERITY, NullCheckOnlyRule.SUGGESTED_ACTION,
                NullCheckOnlyRule.evaluate(method, traversal));
        }
        return fileFindings;
    }

    private static void addIfPresent(List<Finding> out, TestSourceFile file, MethodDeclaration method, String ruleId,
                                      dev.proofjava.analysis.model.Severity severity, String suggestedAction,
                                      Optional<RuleFinding> ruleFinding) {
        if (ruleFinding.isEmpty()) {
            return;
        }
        RuleFinding rf = ruleFinding.get();
        // docs/rules/README.md: a disabled test is still analyzed and still
        // reported - hiding it would be its own kind of dishonesty - but it is
        // reported as INFO rather than at the rule's own severity, and says why.
        // A test that never runs cannot be the reason a suite is weak, so
        // ranking it alongside a live oracle-less test buries the ones that
        // matter. Measured on google/gson: 9 of 33 findings were @Ignore'd
        // benchmarks whose class javadoc says to run them by hand.
        boolean disabled = TestMethods.isDisabled(method);
        String message = disabled ? rf.message() + " (disabled test)" : rf.message();
        dev.proofjava.analysis.model.Severity effectiveSeverity =
            disabled ? dev.proofjava.analysis.model.Severity.INFO : severity;
        String signature = signature(method);
        String fingerprint = Fingerprint.compute(ruleId, file.moduleId(), file.repoRelativePath(), signature);
        Range range = method.getRange().orElse(null);
        int startLine = range != null ? range.begin.line : 1;
        int endLine = range != null ? range.end.line : startLine;
        out.add(new Finding(ruleId, effectiveSeverity, rf.confidence(), file.moduleId(), file.repoRelativePath(),
            startLine, endLine, signature, message, suggestedAction, fingerprint, null));
    }

    /** docs/rules/README.md: {@code <Outer>[.<Inner>...]#<name>(<parameter types as written>)}. */
    private static String signature(MethodDeclaration method) {
        String className = method.findAncestor(ClassOrInterfaceDeclaration.class)
            .flatMap(TypeDeclaration::getFullyQualifiedName)
            .orElse("Unknown");
        String params = method.getParameters().stream().map(p -> p.getType().asString()).collect(Collectors.joining(", "));
        return className + "#" + method.getNameAsString() + "(" + params + ")";
    }
}
