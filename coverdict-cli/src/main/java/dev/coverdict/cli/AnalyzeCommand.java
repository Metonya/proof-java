package dev.coverdict.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import dev.coverdict.analysis.AnalysisException;
import dev.coverdict.analysis.binding.BindingResult;
import dev.coverdict.analysis.binding.ChangedFileClassifier;
import dev.coverdict.analysis.binding.ClassificationResult;
import dev.coverdict.analysis.binding.ModuleBinder;
import dev.coverdict.analysis.jacoco.JacocoReport;
import dev.coverdict.analysis.jacoco.JacocoXmlParser;
import dev.coverdict.analysis.metrics.ExclusionFilter;
import dev.coverdict.analysis.metrics.MetricSet;
import dev.coverdict.analysis.metrics.MetricsEngine;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.RepoPaths;
import dev.coverdict.analysis.model.ResolvedSourceFile;
import dev.coverdict.analysis.oracle.ClasspathLoader;
import dev.coverdict.analysis.oracle.OracleRuleEngine;
import dev.coverdict.analysis.oracle.OracleScanOptions;
import dev.coverdict.analysis.oracle.OracleScanResult;
import dev.coverdict.analysis.pertest.PerTestCollector;
import dev.coverdict.config.ConfigException;
import dev.coverdict.config.ConfigLoader;
import dev.coverdict.config.CoverdictConfig;
import dev.coverdict.analysis.report.ModuleInput;
import dev.coverdict.analysis.report.NewCodeCoverage;
import dev.coverdict.analysis.report.ReportInput;
import dev.coverdict.analysis.report.TextRenderer;
import dev.coverdict.analysis.report.ToolVersion;
import dev.coverdict.analysis.report.VerdictDocument;
import dev.coverdict.analysis.report.VerdictJsonWriter;
import dev.coverdict.analysis.vcs.DiffAcquisition;
import dev.coverdict.analysis.vcs.DiffResult;
import dev.coverdict.analysis.vcs.GitClient;

/**
 * Three diff modes, exactly one required per invocation (docs/M0-CLI-INPUT.md):
 * {@code --no-vcs} (overall coverage only), {@code --uncommitted} (working-tree
 * mode: {@code HEAD} to the working tree), and {@code --base <ref>} (base-ref
 * mode: {@code merge-base(ref, HEAD)} to the working tree, D-16).
 *
 * <p>Two distinct failure shapes, matching the schema's own note that "exit
 * codes 2 and 4 abort before a verdict document is written": a
 * {@link CliUsageException} (bad option syntax, zero or several diff modes)
 * never writes JSON and exits 2; an {@link AnalysisException} always writes a
 * structured incomplete verdict and exits 3 (hard rule 3a). For a diff-mode
 * run, an {@link AnalysisException} thrown while acquiring or classifying the
 * diff (bad {@code --base} ref, missing merge base, git failure) is caught
 * inside {@link #analyze} itself, not propagated to {@link #call} - the
 * overall coverage phase before it already succeeded, and that real data is
 * preserved in the resulting document rather than discarded (D-26).
 */
@Command(name = "analyze", description = "Analyze coverage and test-oracle evidence for a repository.")
class AnalyzeCommand implements Callable<Integer> {

    private static final String DIFF_MODE_NO_VCS = "no-vcs";
    private static final String DIFF_MODE_WORKING_TREE = "working-tree";
    private static final String DIFF_MODE_BASE_REF = "base-ref";
    private static final String FINDINGS_SCOPE_ALL = "all";
    private static final String FINDINGS_SCOPE_CHANGED = "changed";

    @Spec
    private CommandSpec spec;

    @Option(names = "--no-vcs", description = "Overall coverage only; no changed-code numbers.")
    private boolean noVcs;

    @Option(names = "--uncommitted", description = "Working-tree mode: HEAD to the working tree, staged and unstaged.")
    private boolean uncommitted;

    @Option(names = "--base", description = "Base-ref mode: merge-base(ref, HEAD) to the working tree.")
    private String baseRefOption;

    @Option(names = "--repo", description = "Repository root. Default: current working directory.")
    private String repoOption;

    @Option(names = "--report", description = "Bare <path> for the single-module shorthand, or repeatable <id>=<path>.")
    private List<String> reportArgs = new ArrayList<>();

    @Option(names = "--module", description = "Repeatable <id>=<root-dir>, repo-relative.")
    private List<String> moduleArgs = new ArrayList<>();

    @Option(names = "--source-roots", description = "Repeatable <id>=<dir>[,<dir>...]. Default per module: <root>/src/main/java.")
    private List<String> sourceRootsArgs = new ArrayList<>();

    @Option(names = "--test-roots", description = "Repeatable <id>=<dir>[,<dir>...]. Default per module: <root>/src/test/java.")
    private List<String> testRootsArgs = new ArrayList<>();

    @Option(names = "--config", description = "Config file path. Default: coverdict.config.json at the repo root when present. Precedence: command line > config file > defaults.")
    private String configOption;

    @Option(names = "--classpath", description = "Repeatable <id>=<file>, where <file> lists one jar path per line for JavaParser symbol solving (D-17: never silently upgrades confidence).")
    private List<String> classpathArgs = new ArrayList<>();

    @Option(names = "--per-test-report", description = "Collect L2 per-test line coverage evidence for changed production classes via PIT (D-46: evidence only, never a finding). Requires a diff mode; rejected under --no-vcs.")
    private boolean perTestReport;

    @Option(names = "--per-test-classpath", description = "Repeatable <id>=<file>, where <file> lists PIT's exact test runtime classpath for that module, one entry per line (module output directories and dependency jars) - distinct from --classpath, which is an optional JavaParser aid.")
    private List<String> perTestClasspathArgs = new ArrayList<>();

    @Option(names = "--language-level", defaultValue = "17", description = "Java language level for JavaParser (oracle critic) and recorded as provenance.")
    private int languageLevel;

    @Option(names = "--encoding", defaultValue = "UTF-8", description = "Charset used to read test sources for the oracle critic, and recorded as provenance.")
    private String encoding;

    @Option(names = "--coverage-exclusions", description = "Comma-separated sonar.coverage.exclusions globs, one list for the whole run (D-05).")
    private String exclusionsArg;

    @Option(names = "--findings-scope", defaultValue = FINDINGS_SCOPE_ALL,
        description = "Which test sources the L0 oracle rules scan: 'all' (default, every test source under every module's testRoots) or 'changed' (only test files touched by the diff; requires --uncommitted or --base).")
    private String findingsScopeOption;

    @Option(names = "--out", defaultValue = "coverdict-verdict.json", description = "Verdict JSON output path.")
    private String outOption;

    /**
     * Setup and validation, everything that can invalidate the invocation
     * before any analysis runs. Extracted from {@link #call()} (SonarQube
     * java:S3776 - the unextracted method's nesting from four independent
     * validation steps plus config precedence pushed cognitive complexity to
     * 16 against a limit of 15): every failure here is a {@link
     * CliUsageException} or {@link ConfigException}, both handled identically
     * by the one catch in {@link #call()}.
     */
    private record Invocation(Path repoRoot, String diffMode, CoverdictConfig config, List<String> exclusions,
                               List<ModuleDefinition> modules, Map<String, List<String>> reportPathsById,
                               Map<String, String> classpathFilesById, Map<String, String> perTestClasspathFilesById) {
    }

    /** @throws CliUsageException or ConfigException on any invalid invocation - both exit 2, no JSON written. */
    private Invocation validateAndParseInvocation() {
        String diffMode = selectedDiffModeOrThrow();
        Path repoRoot = Path.of(repoOption != null ? repoOption : System.getProperty("user.dir"));
        CoverdictConfig config = ConfigLoader.load(repoRoot, configOption);
        applyConfigPrecedence(config);
        validateFindingsScope(diffMode);
        validatePerTestReport(diffMode);

        List<String> exclusions = exclusionsArg == null || exclusionsArg.isBlank()
            ? List.of()
            : Arrays.stream(exclusionsArg.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();

        Map<String, List<String>> reportPathsById = parseReportArgs();
        List<ModuleDefinition> modules = buildModuleDefinitions(reportPathsById.keySet());
        Map<String, String> classpathFilesById = parseClasspathArgs(modules);
        Map<String, String> perTestClasspathFilesById = parsePerTestClasspathArgs(modules);

        return new Invocation(repoRoot, diffMode, config, exclusions, modules, reportPathsById, classpathFilesById,
            perTestClasspathFilesById);
    }

    /** @throws CliUsageException unless exactly one of --no-vcs/--uncommitted/--base is set. */
    private String selectedDiffModeOrThrow() {
        int modesSelected = (noVcs ? 1 : 0) + (uncommitted ? 1 : 0) + (baseRefOption != null ? 1 : 0);
        if (modesSelected != 1) {
            throw new CliUsageException("exactly one diff mode is required: --no-vcs, --uncommitted, or --base <ref> "
                + "(docs/M0-CLI-INPUT.md).");
        }
        return selectedDiffMode();
    }

    /** @throws CliUsageException on an unknown --findings-scope value, or 'changed' combined with --no-vcs. */
    private void validateFindingsScope(String diffMode) {
        if (!FINDINGS_SCOPE_ALL.equals(findingsScopeOption) && !FINDINGS_SCOPE_CHANGED.equals(findingsScopeOption)) {
            throw new CliUsageException("--findings-scope must be 'all' or 'changed', got: " + findingsScopeOption);
        }
        if (FINDINGS_SCOPE_CHANGED.equals(findingsScopeOption) && DIFF_MODE_NO_VCS.equals(diffMode)) {
            throw new CliUsageException(
                "--findings-scope changed requires a diff mode (--uncommitted or --base <ref>), not --no-vcs.");
        }
    }

    /** @throws CliUsageException when --per-test-report is combined with --no-vcs (undefined without a diff, same pattern as --findings-scope changed). */
    private void validatePerTestReport(String diffMode) {
        if (perTestReport && DIFF_MODE_NO_VCS.equals(diffMode)) {
            throw new CliUsageException("--per-test-report requires a diff mode (--uncommitted or --base <ref>), not --no-vcs.");
        }
    }

    @Override
    public Integer call() {
        Invocation inv;
        try {
            inv = validateAndParseInvocation();
        } catch (CliUsageException | ConfigException e) {
            spec.commandLine().getErr().println("coverdict: " + e.getMessage());
            return ExitCode.INVALID_INPUT.value(); // no JSON written - the invocation itself was invalid
        }

        VerdictDocument doc;
        try {
            doc = analyze(inv);
        } catch (AnalysisException e) {
            doc = incompleteDocument(inv.exclusions(), e, inv.diffMode());
        }

        try (OutputStream out = Files.newOutputStream(Path.of(outOption))) {
            VerdictJsonWriter.write(out, doc);
        } catch (IOException e) {
            spec.commandLine().getErr().println("coverdict: could not write " + outOption + ": " + e.getMessage());
            return ExitCode.INTERNAL_ERROR.value();
        }
        spec.commandLine().getOut().print(TextRenderer.render(doc));
        spec.commandLine().getOut().println("verdict written to " + outOption);

        return doc.complete() ? ExitCode.COMPLETE.value() : ExitCode.INCOMPLETE.value();
    }

    /**
     * M0-CLI-INPUT.md's precedence rule: command line &gt; config file &gt;
     * documented defaults. picocli has already applied the CLI value or the
     * default, and cannot tell those two apart on its own - so the config
     * value is applied only when the parse result shows the user did not
     * actually type the option. {@code coverageExclusions} is replaced, never
     * merged: two half-lists from two sources would be a third list nobody
     * wrote down (D-05 keeps exclusions a single authored set).
     */
    private void applyConfigPrecedence(CoverdictConfig config) {
        if (config.languageLevel() != null && notTypedOnCommandLine("--language-level")) {
            languageLevel = config.languageLevel();
        }
        if (config.encoding() != null && notTypedOnCommandLine("--encoding")) {
            encoding = config.encoding();
        }
        if (config.findingsScope() != null && notTypedOnCommandLine("--findings-scope")) {
            findingsScopeOption = config.findingsScope();
        }
        if (config.coverageExclusions() != null && notTypedOnCommandLine("--coverage-exclusions")) {
            exclusionsArg = String.join(",", config.coverageExclusions());
        }
    }

    private boolean notTypedOnCommandLine(String optionName) {
        return !spec.commandLine().getParseResult().hasMatchedOption(optionName);
    }

    /** Exactly one of {@link #noVcs}/{@link #uncommitted}/{@link #baseRefOption} is set by the time this is called - {@link #call} already validated that. */
    private String selectedDiffMode() {
        if (noVcs) {
            return DIFF_MODE_NO_VCS;
        }
        if (uncommitted) {
            return DIFF_MODE_WORKING_TREE;
        }
        return DIFF_MODE_BASE_REF;
    }

    /** @throws CliUsageException on malformed --report syntax or an option conflict (exit 2, no JSON). */
    private Map<String, List<String>> parseReportArgs() {
        Map<String, List<String>> reportPathsById = new LinkedHashMap<>();
        boolean anyBareReport = false;
        for (String arg : reportArgs) {
            int eq = arg.indexOf('=');
            String moduleId;
            String path;
            if (eq < 0) {
                anyBareReport = true;
                moduleId = "root";
                path = arg;
            } else {
                moduleId = arg.substring(0, eq);
                path = arg.substring(eq + 1);
            }
            reportPathsById.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(path);
        }
        if (anyBareReport && !moduleArgs.isEmpty()) {
            throw new CliUsageException(
                "A bare --report path (single-module shorthand) was combined with --module; "
                    + "use --report id=path for every report once --module is declared.");
        }
        return reportPathsById;
    }

    /** @throws CliUsageException on malformed --module/--source-roots/--test-roots syntax (exit 2, no JSON). */
    private List<ModuleDefinition> buildModuleDefinitions(java.util.Set<String> reportModuleIds) {
        Map<String, String> declaredModuleRoots = parseIdValue(moduleArgs);
        Map<String, List<String>> sourceRootsById = parseIdCsvValue(sourceRootsArgs);
        Map<String, List<String>> testRootsById = parseIdCsvValue(testRootsArgs);

        Map<String, String> moduleRoots = new LinkedHashMap<>(declaredModuleRoots);
        if (reportModuleIds.contains("root") && declaredModuleRoots.isEmpty()) {
            moduleRoots.put("root", "."); // single-module shorthand
        }

        List<ModuleDefinition> modules = new ArrayList<>();
        for (Map.Entry<String, String> entry : moduleRoots.entrySet()) {
            String id = entry.getKey();
            String root = entry.getValue();
            List<String> sourceRoots = sourceRootsById.getOrDefault(id, List.of(RepoPaths.join(root, "src/main/java")));
            List<String> testRoots = testRootsById.getOrDefault(id, List.of(RepoPaths.join(root, "src/test/java")));
            modules.add(new ModuleDefinition(id, root, sourceRoots, testRoots));
        }
        return modules;
    }

    /**
     * @throws CliUsageException on malformed {@code --classpath} syntax, or an
     *         id that names no declared module (hard rule 3a: an id that binds
     *         to nothing is a mistake in the invocation, not something to
     *         quietly ignore - the user would otherwise believe a classpath
     *         was in effect when none was).
     */
    private Map<String, String> parseClasspathArgs(List<ModuleDefinition> modules) {
        Map<String, String> byId = parseIdValue(classpathArgs);
        if (byId.isEmpty()) {
            return byId;
        }
        java.util.Set<String> declaredIds = modules.stream().map(ModuleDefinition::id).collect(Collectors.toSet());
        for (String id : byId.keySet()) {
            if (!declaredIds.contains(id)) {
                throw new CliUsageException("--classpath id '" + id + "' does not match any declared --module id "
                    + declaredIds + ".");
            }
        }
        return byId;
    }

    /** @throws CliUsageException on malformed {@code --per-test-classpath} syntax, or an id that names no declared module (same pattern as {@link #parseClasspathArgs}). */
    private Map<String, String> parsePerTestClasspathArgs(List<ModuleDefinition> modules) {
        Map<String, String> byId = parseIdValue(perTestClasspathArgs);
        if (byId.isEmpty()) {
            return byId;
        }
        java.util.Set<String> declaredIds = modules.stream().map(ModuleDefinition::id).collect(Collectors.toSet());
        for (String id : byId.keySet()) {
            if (!declaredIds.contains(id)) {
                throw new CliUsageException("--per-test-classpath id '" + id + "' does not match any declared --module id "
                    + declaredIds + ".");
            }
        }
        return byId;
    }

    /** @throws AnalysisException when the overall-coverage evidence itself is bad (exit 3, structured incomplete document, nothing preserved). */
    private VerdictDocument analyze(Invocation inv) {
        Path repoRoot = inv.repoRoot();
        List<String> exclusions = inv.exclusions();
        List<ModuleDefinition> modules = inv.modules();
        Map<String, List<String>> reportPathsById = inv.reportPathsById();
        Map<String, String> classpathFilesById = inv.classpathFilesById();
        CoverdictConfig config = inv.config();
        String diffMode = inv.diffMode();

        // A declared module with no bound report has no coverage evidence in
        // --no-vcs mode (M0-CLI-INPUT.md: this is only a hard error when the
        // module has changed Java files, a diff-mode concept this build
        // doesn't have yet) - warn and leave it out of the analyzed set,
        // rather than passing an empty-evidence module through.
        List<AnalysisReason> extraWarnings = new ArrayList<>();
        List<ModuleDefinition> evidencedModules = new ArrayList<>();
        for (ModuleDefinition module : modules) {
            if (reportPathsById.getOrDefault(module.id(), List.of()).isEmpty()) {
                extraWarnings.add(new AnalysisReason("MODULE_WITHOUT_REPORT",
                    "Module '" + module.id() + "' was declared but has no --report bound to it; excluded from the analyzed set.",
                    null, module.id()));
            } else {
                evidencedModules.add(module);
            }
        }

        Map<String, List<JacocoReport>> parsedReportsById = new LinkedHashMap<>();
        Map<String, List<ReportInput>> reportInputsById = new LinkedHashMap<>();
        JacocoXmlParser parser = new JacocoXmlParser();
        for (ModuleDefinition module : evidencedModules) {
            for (String rawPathString : reportPathsById.get(module.id())) {
                // D-22: normalize before it's stored anywhere - a
                // Windows-style argument must not leak backslashes into the
                // JSON, whose $defs/path pattern forbids them.
                String pathString = RepoPaths.normalizeSeparators(rawPathString);
                JacocoReport report = parser.parse(repoRoot.resolve(pathString));
                parsedReportsById.computeIfAbsent(module.id(), k -> new ArrayList<>()).add(report);
                reportInputsById.computeIfAbsent(module.id(), k -> new ArrayList<>())
                    .add(new ReportInput(pathString, "unverified"));
            }
        }

        // Built once for both scan call sites below; its own warnings ride the
        // same channel as MODULE_WITHOUT_REPORT - a classpath entry that could
        // not be opened is visible, never a silent partial resolution (D-17).
        ClasspathLoader.Result classpath = ClasspathLoader.load(repoRoot, classpathFilesById);
        extraWarnings.addAll(classpath.warnings());
        OracleScanOptions scanOptions = OracleScanOptions.defaults()
            .withConfig(config)
            .withTypeSolvers(classpath.solvers());

        BindingResult binding = new ModuleBinder(repoRoot).bind(evidencedModules, parsedReportsById);
        List<ResolvedSourceFile> filtered = ExclusionFilter.apply(binding.resolvedFiles(), exclusions);
        MetricSet overall = MetricsEngine.compute(filtered);

        List<ModuleInput> moduleInputs = evidencedModules.stream()
            .map(m -> new ModuleInput(m.id(), m.root(), m.sourceRoots(), m.testRoots(), reportInputsById.get(m.id())))
            .toList();

        List<AnalysisReason> warnings = new ArrayList<>(extraWarnings);
        warnings.addAll(binding.warnings());

        ToolVersion.Info version = ToolVersion.read();

        if (DIFF_MODE_NO_VCS.equals(diffMode)) {
            // findings-scope=changed is rejected in --no-vcs mode by call() already, so "all" always holds here.
            OracleScanResult scan = OracleRuleEngine.scan(repoRoot, evidencedModules, languageLevel, encoding, null, scanOptions);
            List<AnalysisReason> allReasons = new ArrayList<>(scan.incompleteReasons());
            List<AnalysisReason> noVcsWarnings = new ArrayList<>(warnings);
            noVcsWarnings.addAll(scan.warnings());
            return new VerdictDocument(version.schemaVersion(), version.version(), allReasons.isEmpty(), allReasons,
                languageLevel, encoding, exclusions, moduleInputs, diffMode, findingsScopeOption, null, overall,
                NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), scan.findings(), noVcsWarnings);
        }

        // Diff-mode phase: a failure here does NOT discard the overall data
        // computed above (D-26) - only diff-specific fields fall back.
        try {
            GitClient git = new GitClient(repoRoot);
            DiffResult diffResult = DIFF_MODE_BASE_REF.equals(diffMode)
                ? DiffAcquisition.acquireBaseRef(git, baseRefOption)
                : DiffAcquisition.acquireWorkingTree(git);

            ClassificationResult classification = ChangedFileClassifier.classify(
                diffResult.changedLinesByPath(), diffResult.untrackedFiles(),
                evidencedModules, exclusions, binding.resolvedFiles());

            MetricSet newCode = MetricsEngine.compute(classification.newCodeDataset());

            java.util.Set<String> findingsPaths = FINDINGS_SCOPE_CHANGED.equals(findingsScopeOption)
                ? changedAndUntrackedPaths(diffResult) : null;
            OracleScanResult scan = OracleRuleEngine.scan(repoRoot, evidencedModules, languageLevel, encoding, findingsPaths, scanOptions);

            List<AnalysisReason> allIncompleteReasons = new ArrayList<>(classification.incompleteReasons());
            allIncompleteReasons.addAll(scan.incompleteReasons());
            List<AnalysisReason> allWarnings = new ArrayList<>(warnings);
            allWarnings.addAll(classification.warnings());
            allWarnings.addAll(scan.warnings());

            List<dev.coverdict.analysis.pertest.PerTestModuleEvidence> perTest = null;
            if (perTestReport) {
                PerTestCollector.Result perTestResult = PerTestCollector.collect(repoRoot, evidencedModules,
                    classification.changedFiles(), inv.perTestClasspathFilesById());
                perTest = perTestResult.modules();
                allWarnings.addAll(perTestResult.warnings());
            }

            boolean complete = allIncompleteReasons.isEmpty();
            return new VerdictDocument(version.schemaVersion(), version.version(), complete,
                allIncompleteReasons, languageLevel, encoding, exclusions, moduleInputs,
                diffMode, findingsScopeOption, diffResult.identity(), overall, NewCodeCoverage.available(newCode),
                classification.changedFiles(), scan.findings(), allWarnings, perTest);
        } catch (AnalysisException e) {
            // findings-scope=all does not need the diff that just failed - real
            // oracle evidence is still worth reporting alongside the failure.
            OracleScanResult scan = FINDINGS_SCOPE_ALL.equals(findingsScopeOption)
                ? OracleRuleEngine.scan(repoRoot, evidencedModules, languageLevel, encoding, null)
                : new OracleScanResult(List.of(), List.of());
            List<AnalysisReason> allReasons = new ArrayList<>();
            allReasons.add(new AnalysisReason(e.code(), e.getMessage()));
            allReasons.addAll(scan.incompleteReasons());
            return new VerdictDocument(version.schemaVersion(), version.version(), false,
                allReasons, languageLevel, encoding, exclusions,
                moduleInputs, diffMode, findingsScopeOption, null, overall, NewCodeCoverage.unavailable("unavailable_incomplete"),
                List.of(), scan.findings(), warnings);
        }
    }

    private static java.util.Set<String> changedAndUntrackedPaths(DiffResult diffResult) {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>(diffResult.changedLinesByPath().keySet());
        paths.addAll(diffResult.untrackedFiles());
        return paths;
    }

    private VerdictDocument incompleteDocument(List<String> exclusions, AnalysisException e, String diffMode) {
        ToolVersion.Info version = ToolVersion.read();
        String unavailableStatus = DIFF_MODE_NO_VCS.equals(diffMode) ? "unavailable_no_vcs" : "unavailable_incomplete";
        return new VerdictDocument(
            version.schemaVersion(), version.version(), false,
            List.of(new AnalysisReason(e.code(), e.getMessage())),
            languageLevel, encoding, exclusions, List.of(), diffMode, findingsScopeOption, null,
            MetricsEngine.compute(List.of()), NewCodeCoverage.unavailable(unavailableStatus), List.of(), List.of(), List.of());
    }

    private static Map<String, String> parseIdValue(List<String> args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq < 0) {
                throw new CliUsageException("Expected <id>=<value>, got: " + arg);
            }
            String id = arg.substring(0, eq);
            // hard rule 3a: a repeated id here is ambiguous (which value wins?),
            // not a guessed "last one wins" default. --report is exempt - it has
            // its own parser (parseReportArgs) and repeating a module id there
            // is the legitimate "multiple reports per module" case.
            if (result.containsKey(id)) {
                throw new CliUsageException("Duplicate id '" + id + "' - each id may be declared at most once for this option.");
            }
            result.put(id, arg.substring(eq + 1));
        }
        return result;
    }

    private static Map<String, List<String>> parseIdCsvValue(List<String> args) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : parseIdValue(args).entrySet()) {
            result.put(e.getKey(), Arrays.stream(e.getValue().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        }
        return result;
    }
}
