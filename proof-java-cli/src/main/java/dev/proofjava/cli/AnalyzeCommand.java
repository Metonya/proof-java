package dev.proofjava.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

import dev.proofjava.analysis.AnalysisException;
import dev.proofjava.analysis.binding.BindingResult;
import dev.proofjava.analysis.binding.ChangedFileClassifier;
import dev.proofjava.analysis.binding.ClassificationResult;
import dev.proofjava.analysis.binding.ModuleBinder;
import dev.proofjava.analysis.jacoco.JacocoReport;
import dev.proofjava.analysis.jacoco.JacocoXmlParser;
import dev.proofjava.analysis.metrics.ExclusionFilter;
import dev.proofjava.analysis.metrics.MetricSet;
import dev.proofjava.analysis.metrics.MetricsEngine;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.ModuleDefinition;
import dev.proofjava.analysis.model.RepoPaths;
import dev.proofjava.analysis.model.ResolvedSourceFile;
import dev.proofjava.analysis.mutation.MutationCollector;
import dev.proofjava.analysis.mutation.MutationModuleEvidence;
import dev.proofjava.analysis.mutation.MutationRuleEngine;
import dev.proofjava.analysis.mutation.MutationTargetResolver;
import dev.proofjava.analysis.oracle.ClasspathLoader;
import dev.proofjava.analysis.oracle.OracleRuleEngine;
import dev.proofjava.analysis.oracle.OracleScanOptions;
import dev.proofjava.analysis.oracle.OracleScanResult;
import dev.proofjava.analysis.pertest.PerTestCollector;
import dev.proofjava.analysis.redundancy.RedundancyRuleEngine;
import dev.proofjava.analysis.subprocess.EvidenceDiagnostics;
import dev.proofjava.analysis.subprocess.PitJdkSupport;
import dev.proofjava.config.ConfigException;
import dev.proofjava.config.ConfigLoader;
import dev.proofjava.config.ProofConfig;
import dev.proofjava.analysis.report.FileCoverageBlock;
import dev.proofjava.analysis.report.FileCoverageEntry;
import dev.proofjava.analysis.report.HtmlRenderer;
import dev.proofjava.analysis.report.ModuleInput;
import dev.proofjava.analysis.report.NewCodeCoverage;
import dev.proofjava.analysis.report.ReportInput;
import dev.proofjava.analysis.report.TextRenderer;
import dev.proofjava.analysis.report.ToolVersion;
import dev.proofjava.analysis.report.VerdictDocument;
import dev.proofjava.analysis.report.VerdictJsonWriter;
import dev.proofjava.analysis.vcs.DiffAcquisition;
import dev.proofjava.analysis.vcs.DiffResult;
import dev.proofjava.analysis.vcs.GitClient;

/**
 * Three diff modes, exactly one required per invocation (docs/INPUT-MODEL.md):
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
@Command(name = "analyze", mixinStandardHelpOptions = true,
    description = "Analyze coverage and test-oracle evidence for a repository.")
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

    @Option(names = "--config", description = "Config file path. Default: proof.config.json at the repo root when present. Precedence: command line > config file > defaults.")
    private String configOption;

    @Option(names = "--classpath", description = "Repeatable <id>=<file>, where <file> lists one jar path per line for JavaParser symbol solving (D-17: never silently upgrades confidence).")
    private List<String> classpathArgs = new ArrayList<>();

    @Option(names = "--per-test-report", description = "Collect L2 per-test line coverage evidence for changed production classes via PIT (D-46: evidence only, never a finding by itself). Optional message enrichment for SUBSUMED_TEST when --mutation-report is also set (D-61). Requires a diff mode; rejected under --no-vcs.")
    private boolean perTestReport;

    @Option(names = "--per-test-classpath", description = "Repeatable <id>=<file>, where <file> lists PIT's exact test runtime classpath for that module, one entry per line (module output directories and dependency jars) - distinct from --classpath, which is an optional JavaParser aid.")
    private List<String> perTestClasspathArgs = new ArrayList<>();

    @Option(names = "--per-test-target", description = "Repeatable <id>=<FQCN>, one or more explicit classes to collect L2 per-test line coverage for - independent of the diff (Faz 14a, same pattern as --mutation-target). All-or-nothing: when at least one is given, every module's diff-derived targets are ignored entirely for L2, including modules with none of their own. Requires --per-test-report; lifts the --no-vcs restriction on it.")
    private List<String> perTestTargetArgs = new ArrayList<>();

    @Option(names = "--per-test-timeout", defaultValue = "120", description = "Wall-clock budget in seconds for one module's per-test coverage run before it is force-killed (default 120s - generous for a diff-scoped target, per D-52's measured scale; raise it for a large explicit --per-test-target list spanning many classes at once).")
    private long perTestTimeoutSeconds;

    @Option(names = "--mutation-report", description = "Collect L3 mutation evidence via PIT (D-56: RETURNS+VOID_METHOD_CALLS gregor mutators). Feeds PSEUDO_TESTED_METHOD and SUBSUMED_TEST (D-61's kill-set subsumption). Requires a diff mode unless --mutation-target is also given; rejected under bare --no-vcs.")
    private boolean mutationReport;

    @Option(names = "--mutation-target", description = "Repeatable <id>=<FQCN>, one or more explicit classes to mutate - independent of the diff (Plan.md Faz 2). All-or-nothing: when at least one is given, every module's diff-derived targets are ignored entirely, including modules with none of their own. Requires --mutation-report; lifts the --no-vcs restriction on it.")
    private List<String> mutationTargetArgs = new ArrayList<>();

    @Option(names = "--mutation-classpath", description = "Repeatable <id>=<file>, same list-file shape as --per-test-classpath - a separate flag because L3 mutation evidence is a separate, independently opt-in evidence layer (D-56).")
    private List<String> mutationClasspathArgs = new ArrayList<>();

    @Option(names = "--mutation-timeout", defaultValue = "300", description = "Idle timeout in seconds: a module's mutation run is stopped when no class has completed for this long (D-85). Not a total budget - a run that keeps completing classes keeps going, however long the module takes. Raise it only if a single class legitimately needs longer than this.")
    private long mutationTimeoutSeconds;

    @Option(names = "--diagnostics-dir", description = "Directory for per-module L2/L3 subprocess logs. Also turns the engine verbose, which is the only way to see why its own coverage minion died (D-64). Off by default: verbose output with nowhere to land is just a slower run.")
    private String diagnosticsDirOption;

    @Option(names = "--diagnostics-level", defaultValue = "errors",
        description = "How much the engine itself logs into --diagnostics-dir: 'errors' (default) keeps it quiet, so"
            + " the log holds its failures and its summary; 'verbose' adds a line per mutant per test, which is what"
            + " makes a stuck coverage minion diagnosable but reached 184 MB for one module on a real repo (D-91)."
            + " Ignored without --diagnostics-dir.")
    private String diagnosticsLevelOption;

    @Option(names = "--language-level", defaultValue = "17", description = "Java language level for JavaParser (oracle critic), 8-21, and recorded as provenance.")
    private int languageLevel;

    @Option(names = "--encoding", defaultValue = "UTF-8", description = "Charset used to read test sources for the oracle critic, and recorded as provenance.")
    private String encoding;

    @Option(names = "--coverage-exclusions", description = "Comma-separated sonar.coverage.exclusions globs, one list for the whole run (D-05).")
    private String exclusionsArg;

    @Option(names = "--file-coverage", description = "Emit the fileCoverage block: every filtered source file's own line-level coverage and per-file MetricSet, plus the excluded path list (Plan.md Faz 1). Opt-in - can add several MB on a large report.")
    private boolean fileCoverage;

    @Option(names = "--findings-scope", defaultValue = FINDINGS_SCOPE_ALL,
        description = "Which test sources the L0 oracle rules scan: 'all' (default, every test source under every module's testRoots) or 'changed' (only test files touched by the diff; requires --uncommitted or --base).")
    private String findingsScopeOption;

    @Option(names = "--out", defaultValue = "proof-verdict.json", description = "Verdict JSON output path.")
    private String outOption;

    @Option(names = "--html-report", description = "Optional human-readable HTML report path, rendered from the same verdict document as --out (hard rule 7). Off by default.")
    private String htmlReportOption;

    /**
     * Setup and validation, everything that can invalidate the invocation
     * before any analysis runs. Extracted from {@link #call()} (SonarQube
     * java:S3776 - the unextracted method's nesting from four independent
     * validation steps plus config precedence pushed cognitive complexity to
     * 16 against a limit of 15): every failure here is a {@link
     * CliUsageException} or {@link ConfigException}, both handled identically
     * by the one catch in {@link #call()}.
     */
    private record Invocation(Path repoRoot, String diffMode, ProofConfig config, List<String> exclusions,
                               List<ModuleDefinition> modules, Map<String, List<String>> reportPathsById,
                               Map<String, String> classpathFilesById, Map<String, String> perTestClasspathFilesById,
                               Map<String, List<String>> perTestTargetFqcnsById,
                               Map<String, String> mutationClasspathFilesById,
                               Map<String, List<String>> mutationTargetFqcnsById) {
    }

    /** @throws CliUsageException or ConfigException on any invalid invocation - both exit 2, no JSON written. */
    private Invocation validateAndParseInvocation() {
        String diffMode = selectedDiffModeOrThrow();
        Path repoRoot = Path.of(repoOption != null ? repoOption : System.getProperty("user.dir"));
        ProofConfig config = ConfigLoader.load(repoRoot, configOption);
        applyConfigPrecedence(config);
        validateFindingsScope(diffMode);
        validateLanguageLevel();

        List<String> exclusions = exclusionsArg == null || exclusionsArg.isBlank()
            ? List.of()
            : Arrays.stream(exclusionsArg.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();

        ModuleSource moduleSource = moduleArgs.isEmpty() && reportArgs.isEmpty() && !config.modules().isEmpty()
            ? fromConfigModules(config)
            : fromCliArgs();
        Map<String, List<String>> reportPathsById = moduleSource.reportPathsById();
        List<ModuleDefinition> modules = moduleSource.modules();
        Map<String, String> classpathFilesById = parseClasspathArgs(modules);
        Map<String, String> perTestClasspathFilesById = mergeConfigThenCli(
            moduleSource.perTestClasspathFilesById(), parsePerTestClasspathArgs(modules));
        Map<String, List<String>> perTestTargetFqcnsById = parsePerTestTargetArgs(modules);
        validatePerTestReport(diffMode, perTestTargetFqcnsById);
        Map<String, String> mutationClasspathFilesById = mergeConfigThenCli(
            moduleSource.mutationClasspathFilesById(), parseMutationClasspathArgs(modules));
        Map<String, List<String>> mutationTargetFqcnsById = parseMutationTargetArgs(modules);
        validateMutationReport(diffMode, mutationTargetFqcnsById);

        return new Invocation(repoRoot, diffMode, config, exclusions, modules, reportPathsById, classpathFilesById,
            perTestClasspathFilesById, perTestTargetFqcnsById, mutationClasspathFilesById, mutationTargetFqcnsById);
    }

    /** @throws CliUsageException unless exactly one of --no-vcs/--uncommitted/--base is set. */
    private String selectedDiffModeOrThrow() {
        int modesSelected = (noVcs ? 1 : 0) + (uncommitted ? 1 : 0) + (baseRefOption != null ? 1 : 0);
        if (modesSelected != 1) {
            throw new CliUsageException("exactly one diff mode is required: --no-vcs, --uncommitted, or --base <ref> "
                + "(docs/INPUT-MODEL.md).");
        }
        return selectedDiffMode();
    }

    /**
     * @throws CliUsageException when the level is outside what JavaParser
     *         supports. Rejected rather than clamped: parsing Java 22 sources
     *         at level 17 does not fail loudly, it just yields
     *         {@code UNPARSEABLE_TEST_SOURCE} findings that read as a defect in
     *         the user's tests rather than a limit of this tool.
     */
    private void validateLanguageLevel() {
        if (languageLevel < OracleRuleEngine.MIN_LANGUAGE_LEVEL
            || languageLevel > OracleRuleEngine.MAX_LANGUAGE_LEVEL) {
            throw new CliUsageException("--language-level must be between "
                + OracleRuleEngine.MIN_LANGUAGE_LEVEL + " and " + OracleRuleEngine.MAX_LANGUAGE_LEVEL
                + " (the parser this release embeds), got: " + languageLevel);
        }
    }

    /**
     * @return true when the running JDK is too new for the embedded mutation
     *         engine, after recording why. The caller then skips collection, so
     *         the run reports incomplete (exit 3) rather than the silent
     *         zero-finding green PIT would otherwise produce - see
     *         {@link PitJdkSupport}.
     */
    private boolean pitEvidenceBlocked(List<AnalysisReason> incompleteReasons, String code, String flag) {
        if (PitJdkSupport.isRuntimeSupported()) {
            return false;
        }
        incompleteReasons.add(new AnalysisReason(code, PitJdkSupport.unsupportedMessage(flag)));
        return true;
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

    /**
     * @throws CliUsageException when --per-test-target is given without
     *         --per-test-report (nothing would ever consume it), or when
     *         --per-test-report is combined with --no-vcs and no
     *         --per-test-target was given (undefined without a diff, same
     *         pattern as --findings-scope changed) - --per-test-target lifts
     *         that restriction (Faz 14a: it names its own targets, no diff
     *         needed, mirrors --mutation-target's validateMutationReport).
     */
    private void validatePerTestReport(String diffMode, Map<String, List<String>> perTestTargetFqcnsById) {
        if (!perTestTargetFqcnsById.isEmpty() && !perTestReport) {
            throw new CliUsageException("--per-test-target requires --per-test-report.");
        }
        if (perTestReport && DIFF_MODE_NO_VCS.equals(diffMode) && perTestTargetFqcnsById.isEmpty()) {
            throw new CliUsageException("--per-test-report requires a diff mode (--uncommitted or --base <ref>) "
                + "unless --per-test-target is also given, not --no-vcs.");
        }
    }

    /**
     * @throws CliUsageException when --mutation-target is given without
     *         --mutation-report (nothing would ever consume it), or when
     *         --mutation-report is combined with --no-vcs and no
     *         --mutation-target was given (same reason as --per-test-report:
     *         no diff, no targets) - --mutation-target lifts that
     *         restriction (Plan.md Faz 2: it names its own targets, no diff
     *         needed).
     */
    private void validateMutationReport(String diffMode, Map<String, List<String>> mutationTargetFqcnsById) {
        if (!mutationTargetFqcnsById.isEmpty() && !mutationReport) {
            throw new CliUsageException("--mutation-target requires --mutation-report.");
        }
        if (mutationReport && DIFF_MODE_NO_VCS.equals(diffMode) && mutationTargetFqcnsById.isEmpty()) {
            throw new CliUsageException("--mutation-report requires a diff mode (--uncommitted or --base <ref>) "
                + "unless --mutation-target is also given, not --no-vcs.");
        }
    }

    @Override
    public Integer call() {
        Invocation inv;
        try {
            inv = validateAndParseInvocation();
        } catch (CliUsageException | ConfigException e) {
            spec.commandLine().getErr().println("proof-java: " + e.getMessage());
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
            spec.commandLine().getErr().println("proof-java: could not write " + outOption + ": " + e.getMessage());
            return ExitCode.INTERNAL_ERROR.value();
        }

        if (htmlReportOption != null && !htmlReportOption.isBlank()) {
            try (OutputStream htmlOut = Files.newOutputStream(Path.of(htmlReportOption))) {
                htmlOut.write(HtmlRenderer.render(doc).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                spec.commandLine().getErr().println("proof-java: could not write " + htmlReportOption + ": " + e.getMessage());
                return ExitCode.INTERNAL_ERROR.value();
            }
        }

        spec.commandLine().getOut().print(TextRenderer.render(doc));
        spec.commandLine().getOut().println("verdict written to " + outOption);

        return doc.complete() ? ExitCode.COMPLETE.value() : ExitCode.INCOMPLETE.value();
    }

    /**
     * INPUT-MODEL.md's precedence rule: command line &gt; config file &gt;
     * documented defaults. picocli has already applied the CLI value or the
     * default, and cannot tell those two apart on its own - so the config
     * value is applied only when the parse result shows the user did not
     * actually type the option. {@code coverageExclusions} is replaced, never
     * merged: two half-lists from two sources would be a third list nobody
     * wrote down (D-05 keeps exclusions a single authored set).
     */
    private void applyConfigPrecedence(ProofConfig config) {
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

    /**
     * D-66: where a run's module/report/L2/L3-classpath bindings come from
     * - either every {@code --module}/{@code --report} flag on the command
     * line, or (only when the command line declares none at all)
     * {@code proof.config.json}'s {@code modules} array, generated by
     * {@code proof-java doctor --write-config}. The two are never partially
     * merged - a single {@code --module} on the command line makes
     * {@link #validateAndParseInvocation} ignore the config's {@code
     * modules} entirely, same all-or-nothing rule {@link
     * #applyConfigPrecedence} already applies to {@code coverageExclusions}.
     */
    private record ModuleSource(List<ModuleDefinition> modules, Map<String, List<String>> reportPathsById,
                                 Map<String, String> perTestClasspathFilesById,
                                 Map<String, String> mutationClasspathFilesById) {
    }

    private ModuleSource fromCliArgs() {
        Map<String, List<String>> reportPathsById = parseReportArgs();
        List<ModuleDefinition> modules = buildModuleDefinitions(reportPathsById.keySet());
        return new ModuleSource(modules, reportPathsById, Map.of(), Map.of());
    }

    /** {@code ConfigLoader} already rejects a duplicate id or a missing id/root - nothing left to validate here. */
    private static ModuleSource fromConfigModules(ProofConfig config) {
        List<ModuleDefinition> modules = new ArrayList<>();
        Map<String, List<String>> reportPathsById = new LinkedHashMap<>();
        Map<String, String> perTestClasspathFilesById = new LinkedHashMap<>();
        Map<String, String> mutationClasspathFilesById = new LinkedHashMap<>();
        for (ProofConfig.ModuleConfig m : config.modules()) {
            List<String> sourceRoots = m.sourceRoots() != null && !m.sourceRoots().isEmpty()
                ? m.sourceRoots() : List.of(RepoPaths.join(m.root(), "src/main/java"));
            List<String> testRoots = m.testRoots() != null && !m.testRoots().isEmpty()
                ? m.testRoots() : List.of(RepoPaths.join(m.root(), "src/test/java"));
            modules.add(new ModuleDefinition(m.id(), m.root(), sourceRoots, testRoots));
            if (m.report() != null) {
                reportPathsById.put(m.id(), List.of(m.report()));
            }
            if (m.perTestClasspath() != null) {
                perTestClasspathFilesById.put(m.id(), m.perTestClasspath());
            }
            if (m.mutationClasspath() != null) {
                mutationClasspathFilesById.put(m.id(), m.mutationClasspath());
            }
        }
        return new ModuleSource(modules, reportPathsById, perTestClasspathFilesById, mutationClasspathFilesById);
    }

    /** Command-line &gt; config for L2/L3 classpaths too, at the per-module id level - not all-or-nothing like {@link ModuleSource} itself, since a user may reasonably override one module's classpath without restating every other module on the command line. */
    private static Map<String, String> mergeConfigThenCli(Map<String, String> fromConfig, Map<String, String> fromCli) {
        if (fromConfig.isEmpty()) {
            return fromCli;
        }
        Map<String, String> merged = new LinkedHashMap<>(fromConfig);
        merged.putAll(fromCli);
        return merged;
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

    /** @throws CliUsageException on malformed {@code --mutation-classpath} syntax, or an id that names no declared module (same pattern as {@link #parsePerTestClasspathArgs}). */
    private Map<String, String> parseMutationClasspathArgs(List<ModuleDefinition> modules) {
        Map<String, String> byId = parseIdValue(mutationClasspathArgs);
        if (byId.isEmpty()) {
            return byId;
        }
        java.util.Set<String> declaredIds = modules.stream().map(ModuleDefinition::id).collect(Collectors.toSet());
        for (String id : byId.keySet()) {
            if (!declaredIds.contains(id)) {
                throw new CliUsageException("--mutation-classpath id '" + id + "' does not match any declared --module id "
                    + declaredIds + ".");
            }
        }
        return byId;
    }

    /**
     * @throws CliUsageException on malformed {@code --per-test-target} syntax
     *         (must be {@code <id>=<FQCN>}, no bare form), or an id that names
     *         no declared module (same pattern as {@link #parseMutationTargetArgs}).
     *         Unlike those id-keyed options, an id may repeat here - naming
     *         several classes in one module is the normal case, not a mistake.
     */
    private Map<String, List<String>> parsePerTestTargetArgs(List<ModuleDefinition> modules) {
        Map<String, List<String>> byId = new LinkedHashMap<>();
        for (String arg : perTestTargetArgs) {
            int eq = arg.indexOf('=');
            if (eq < 0) {
                throw new CliUsageException("Expected --per-test-target <id>=<FQCN>, got: " + arg);
            }
            byId.computeIfAbsent(arg.substring(0, eq), k -> new ArrayList<>()).add(arg.substring(eq + 1));
        }
        if (byId.isEmpty()) {
            return byId;
        }
        java.util.Set<String> declaredIds = modules.stream().map(ModuleDefinition::id).collect(Collectors.toSet());
        for (String id : byId.keySet()) {
            if (!declaredIds.contains(id)) {
                throw new CliUsageException("--per-test-target id '" + id + "' does not match any declared --module id "
                    + declaredIds + ".");
            }
        }
        return byId;
    }

    /**
     * @throws CliUsageException on malformed {@code --mutation-target} syntax
     *         (must be {@code <id>=<FQCN>}, no bare form), or an id that names
     *         no declared module (same pattern as {@link #parseClasspathArgs}).
     *         Unlike those id-keyed options, an id may repeat here - naming
     *         several classes in one module is the normal case, not a mistake.
     */
    private Map<String, List<String>> parseMutationTargetArgs(List<ModuleDefinition> modules) {
        Map<String, List<String>> byId = new LinkedHashMap<>();
        for (String arg : mutationTargetArgs) {
            int eq = arg.indexOf('=');
            if (eq < 0) {
                throw new CliUsageException("Expected --mutation-target <id>=<FQCN>, got: " + arg);
            }
            byId.computeIfAbsent(arg.substring(0, eq), k -> new ArrayList<>()).add(arg.substring(eq + 1));
        }
        if (byId.isEmpty()) {
            return byId;
        }
        java.util.Set<String> declaredIds = modules.stream().map(ModuleDefinition::id).collect(Collectors.toSet());
        for (String id : byId.keySet()) {
            if (!declaredIds.contains(id)) {
                throw new CliUsageException("--mutation-target id '" + id + "' does not match any declared --module id "
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
        ProofConfig config = inv.config();
        String diffMode = inv.diffMode();

        // A declared module with no bound report has no coverage evidence in
        // --no-vcs mode (INPUT-MODEL.md: this is only a hard error when the
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
        ExclusionFilter.Partition partition = ExclusionFilter.partition(binding.resolvedFiles(), exclusions);
        List<ResolvedSourceFile> filtered = partition.kept();
        MetricSet overall = MetricsEngine.compute(filtered);
        FileCoverageBlock fileCoverageBlock = fileCoverage ? buildFileCoverageBlock(partition) : null;

        List<ModuleInput> moduleInputs = evidencedModules.stream()
            .map(m -> new ModuleInput(m.id(), m.root(), m.sourceRoots(), m.testRoots(), reportInputsById.get(m.id())))
            .toList();

        List<AnalysisReason> warnings = new ArrayList<>(extraWarnings);
        warnings.addAll(binding.warnings());

        SharedContext ctx = new SharedContext(evidencedModules, scanOptions, warnings, moduleInputs, overall,
            fileCoverageBlock, ToolVersion.read(), binding.resolvedFiles());

        return DIFF_MODE_NO_VCS.equals(diffMode) ? analyzeNoVcs(inv, ctx) : analyzeDiffMode(inv, ctx);
    }

    /**
     * Everything both {@code analyze} branches below need that the setup
     * phase above already computed once (SonarQube java:S3776/S6541 -
     * {@code analyze} itself stayed a "Brain Method" even after {@link
     * #collectPerTestEvidence}/{@link #collectMutationEvidence} were already
     * extracted; splitting its two diff-mode branches out is what the LOC/
     * complexity budget needed next). Not in {@link Invocation} because
     * these are computed, not supplied - {@code inv} is this call's raw
     * input, this is its derived, shared intermediate state.
     */
    private record SharedContext(List<ModuleDefinition> evidencedModules, OracleScanOptions scanOptions,
                                  List<AnalysisReason> warnings, List<ModuleInput> moduleInputs, MetricSet overall,
                                  FileCoverageBlock fileCoverageBlock, ToolVersion.Info version,
                                  List<ResolvedSourceFile> resolvedFiles) {
    }

    /**
     * {@code --no-vcs}: {@code findings-scope=changed} is rejected in this
     * mode by {@code call()} already, so "all" always holds here.
     * {@code --per-test-target}/{@code --mutation-target} are the only way
     * {@code --per-test-report}/{@code --mutation-report} reach this branch
     * (bare {@code --no-vcs} + either is rejected earlier) - both name their
     * own targets, so no diff is needed (Faz 14a / Plan.md Faz 2).
     */
    private VerdictDocument analyzeNoVcs(Invocation inv, SharedContext ctx) {
        Path repoRoot = inv.repoRoot();
        OracleScanResult scan = OracleRuleEngine.scan(repoRoot, ctx.evidencedModules(), languageLevel, encoding,
            null, ctx.scanOptions());
        List<AnalysisReason> allReasons = new ArrayList<>(scan.incompleteReasons());
        List<AnalysisReason> noVcsWarnings = new ArrayList<>(ctx.warnings());
        noVcsWarnings.addAll(scan.warnings());
        List<Finding> noVcsFindings = new ArrayList<>(scan.findings());

        List<dev.proofjava.analysis.pertest.PerTestModuleEvidence> perTest = null;
        if (perTestReport && !pitEvidenceBlocked(allReasons, "PER_TEST_JDK_UNSUPPORTED", "--per-test-report")) {
            PerTestOutcome outcome = collectPerTestEvidence(repoRoot, ctx.evidencedModules(), List.of(),
                inv.perTestClasspathFilesById(), inv.perTestTargetFqcnsById(), buildDiagnostics());
            perTest = outcome.perTest();
            noVcsWarnings.addAll(outcome.warnings());
            allReasons.addAll(outcome.incompleteReasons());
        }

        List<MutationModuleEvidence> mutation = null;
        if (mutationReport && !pitEvidenceBlocked(allReasons, "MUTATION_JDK_UNSUPPORTED", "--mutation-report")) {
            MutationOutcome outcome = collectMutationEvidence(repoRoot, ctx.evidencedModules(), List.of(),
                inv.mutationClasspathFilesById(), inv.mutationTargetFqcnsById(), buildDiagnostics());
            mutation = outcome.mutation();
            noVcsFindings.addAll(outcome.findings());
            noVcsWarnings.addAll(outcome.warnings());
            allReasons.addAll(outcome.incompleteReasons());
        }

        ToolVersion.Info version = ctx.version();
        return new VerdictDocument(version.schemaVersion(), version.version(), allReasons.isEmpty(), allReasons,
            languageLevel, encoding, inv.exclusions(), ctx.moduleInputs(), inv.diffMode(), findingsScopeOption, null,
            ctx.overall(), NewCodeCoverage.unavailable("unavailable_no_vcs"), List.of(), noVcsFindings, noVcsWarnings,
            perTest, mutation, ctx.fileCoverageBlock());
    }

    /**
     * A failure here does NOT discard the overall data the setup phase
     * already computed (D-26) - only diff-specific fields fall back.
     */
    private VerdictDocument analyzeDiffMode(Invocation inv, SharedContext ctx) {
        Path repoRoot = inv.repoRoot();
        String diffMode = inv.diffMode();
        ToolVersion.Info version = ctx.version();
        try {
            GitClient git = new GitClient(repoRoot);
            DiffResult diffResult = DIFF_MODE_BASE_REF.equals(diffMode)
                ? DiffAcquisition.acquireBaseRef(git, baseRefOption)
                : DiffAcquisition.acquireWorkingTree(git);

            ClassificationResult classification = ChangedFileClassifier.classify(
                diffResult.changedLinesByPath(), diffResult.untrackedFiles(),
                ctx.evidencedModules(), inv.exclusions(), ctx.resolvedFiles());

            MetricSet newCode = MetricsEngine.compute(classification.newCodeDataset());

            java.util.Set<String> findingsPaths = FINDINGS_SCOPE_CHANGED.equals(findingsScopeOption)
                ? changedAndUntrackedPaths(diffResult) : null;
            OracleScanResult scan = OracleRuleEngine.scan(repoRoot, ctx.evidencedModules(), languageLevel, encoding,
                findingsPaths, ctx.scanOptions());

            List<AnalysisReason> allIncompleteReasons = new ArrayList<>(classification.incompleteReasons());
            allIncompleteReasons.addAll(scan.incompleteReasons());
            List<AnalysisReason> allWarnings = new ArrayList<>(ctx.warnings());
            allWarnings.addAll(classification.warnings());
            allWarnings.addAll(scan.warnings());

            EvidenceDiagnostics diagnostics = buildDiagnostics();

            List<dev.proofjava.analysis.pertest.PerTestModuleEvidence> perTest = null;
            if (perTestReport
                && !pitEvidenceBlocked(allIncompleteReasons, "PER_TEST_JDK_UNSUPPORTED", "--per-test-report")) {
                // --per-test-target takes priority over diff-derived targets,
                // all-or-nothing across every module in this run (Faz 14a,
                // mirrors --mutation-target's collectMutationEvidence).
                PerTestOutcome outcome = collectPerTestEvidence(repoRoot, ctx.evidencedModules(), classification.changedFiles(),
                    inv.perTestClasspathFilesById(), inv.perTestTargetFqcnsById(), diagnostics);
                perTest = outcome.perTest();
                allWarnings.addAll(outcome.warnings());
                allIncompleteReasons.addAll(outcome.incompleteReasons());
            }

            List<Finding> allFindings = new ArrayList<>(scan.findings());
            List<MutationModuleEvidence> mutation = null;
            if (mutationReport
                && !pitEvidenceBlocked(allIncompleteReasons, "MUTATION_JDK_UNSUPPORTED", "--mutation-report")) {
                MutationOutcome outcome = collectMutationEvidence(repoRoot, ctx.evidencedModules(),
                    classification.changedFiles(), inv.mutationClasspathFilesById(), inv.mutationTargetFqcnsById(),
                    diagnostics);
                mutation = outcome.mutation();
                allFindings.addAll(outcome.findings());
                allWarnings.addAll(outcome.warnings());
                allIncompleteReasons.addAll(outcome.incompleteReasons());
            }

            boolean complete = allIncompleteReasons.isEmpty();
            return new VerdictDocument(version.schemaVersion(), version.version(), complete,
                allIncompleteReasons, languageLevel, encoding, inv.exclusions(), ctx.moduleInputs(),
                diffMode, findingsScopeOption, diffResult.identity(), ctx.overall(), NewCodeCoverage.available(newCode),
                classification.changedFiles(), allFindings, allWarnings, perTest, mutation, ctx.fileCoverageBlock());
        } catch (AnalysisException e) {
            // findings-scope=all does not need the diff that just failed - real
            // oracle evidence is still worth reporting alongside the failure.
            OracleScanResult scan = FINDINGS_SCOPE_ALL.equals(findingsScopeOption)
                ? OracleRuleEngine.scan(repoRoot, ctx.evidencedModules(), languageLevel, encoding, null)
                : new OracleScanResult(List.of(), List.of());
            List<AnalysisReason> allReasons = new ArrayList<>();
            allReasons.add(new AnalysisReason(e.code(), e.getMessage()));
            allReasons.addAll(scan.incompleteReasons());
            return new VerdictDocument(version.schemaVersion(), version.version(), false,
                allReasons, languageLevel, encoding, inv.exclusions(),
                ctx.moduleInputs(), diffMode, findingsScopeOption, null, ctx.overall(), NewCodeCoverage.unavailable("unavailable_incomplete"),
                List.of(), scan.findings(), ctx.warnings(), null, null, ctx.fileCoverageBlock());
        }
    }

    private record MutationOutcome(List<MutationModuleEvidence> mutation, List<Finding> findings,
                                    List<AnalysisReason> warnings, List<AnalysisReason> incompleteReasons) {
    }

    private record PerTestOutcome(List<dev.proofjava.analysis.pertest.PerTestModuleEvidence> perTest,
                                   List<AnalysisReason> warnings, List<AnalysisReason> incompleteReasons) {
    }

    /**
     * Shared by both {@code analyze} branches that can run {@code
     * --per-test-report} (SonarQube java:S6541 - keeping this branching out
     * of {@code analyze} itself is what keeps that method under the
     * complexity/LOC thresholds, same reason {@link #collectMutationEvidence}
     * was already extracted). {@code --per-test-target} takes priority over
     * {@code changedFiles}, all-or-nothing, mirroring {@link
     * #collectMutationEvidence}'s target-vs-diff precedence (Faz 14a).
     */
    private PerTestOutcome collectPerTestEvidence(Path repoRoot, List<ModuleDefinition> evidencedModules,
                                                    List<ChangedFile> changedFiles,
                                                    Map<String, String> perTestClasspathFilesById,
                                                    Map<String, List<String>> perTestTargetFqcnsById,
                                                    EvidenceDiagnostics diagnostics) {
        List<AnalysisReason> warnings = new ArrayList<>();
        PerTestCollector.Result result;
        Duration timeout = Duration.ofSeconds(perTestTimeoutSeconds);
        if (!perTestTargetFqcnsById.isEmpty()) {
            dev.proofjava.analysis.pertest.PerTestTargetResolver.Result targets =
                dev.proofjava.analysis.pertest.PerTestTargetResolver.resolve(repoRoot, evidencedModules, perTestTargetFqcnsById);
            warnings.addAll(targets.warnings());
            result = PerTestCollector.collectForTargets(repoRoot, evidencedModules, targets.targetGlobsById(),
                perTestClasspathFilesById, timeout, diagnostics);
        } else {
            result = PerTestCollector.collect(repoRoot, evidencedModules, changedFiles, perTestClasspathFilesById, timeout, diagnostics);
        }
        warnings.addAll(result.warnings());
        return new PerTestOutcome(result.modules(), warnings, result.incompleteReasons());
    }

    /**
     * Shared by both {@code analyze} branches that can run {@code
     * --mutation-report}: {@code --mutation-target} (Plan.md Faz 2, works
     * under {@code --no-vcs} too - {@code changedFiles} is then {@code
     * List.of()} and never consulted) takes priority over diff-derived
     * targets, all-or-nothing across every module in this run (D-66's
     * config-modules precedent - see {@link MutationTargetResolver}).
     * {@link RedundancyRuleEngine} runs in both branches regardless of which
     * targeting mode produced {@code mutation} - it has no changed-files
     * dependency of its own (verified while wiring this in: {@code
     * SubsumedTestRule}/{@code TestLocator} resolve a test's path straight
     * off the module's declared test roots, never through a changed-files
     * index).
     */
    private MutationOutcome collectMutationEvidence(Path repoRoot, List<ModuleDefinition> evidencedModules,
                                                      List<ChangedFile> changedFiles,
                                                      Map<String, String> mutationClasspathFilesById,
                                                      Map<String, List<String>> mutationTargetFqcnsById,
                                                      EvidenceDiagnostics diagnostics) {
        List<AnalysisReason> warnings = new ArrayList<>();
        List<AnalysisReason> incompleteReasons = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        List<MutationModuleEvidence> mutation;
        Duration budget = Duration.ofSeconds(mutationTimeoutSeconds);

        if (!mutationTargetFqcnsById.isEmpty()) {
            MutationTargetResolver.Result targets = MutationTargetResolver.resolve(repoRoot, evidencedModules, mutationTargetFqcnsById);
            warnings.addAll(targets.warnings());
            MutationCollector.Result mutationResult = MutationCollector.collectForTargets(repoRoot, evidencedModules,
                targets.targetGlobsById(), mutationClasspathFilesById, budget, diagnostics);
            mutation = mutationResult.modules();
            warnings.addAll(mutationResult.warnings());
            incompleteReasons.addAll(mutationResult.incompleteReasons());
            MutationRuleEngine.Result ruleResult = MutationRuleEngine.evaluate(evidencedModules,
                targets.classNameToPathByModuleId(), mutation);
            findings.addAll(ruleResult.findings());
            warnings.addAll(ruleResult.warnings());
        } else {
            MutationCollector.Result mutationResult = MutationCollector.collect(repoRoot, evidencedModules,
                changedFiles, mutationClasspathFilesById, budget, diagnostics);
            mutation = mutationResult.modules();
            warnings.addAll(mutationResult.warnings());
            incompleteReasons.addAll(mutationResult.incompleteReasons());
            MutationRuleEngine.Result ruleResult = MutationRuleEngine.evaluate(evidencedModules, changedFiles, mutation);
            findings.addAll(ruleResult.findings());
            warnings.addAll(ruleResult.warnings());
        }

        RedundancyRuleEngine.Result redundancyResult = RedundancyRuleEngine.evaluate(repoRoot, evidencedModules, mutation);
        findings.addAll(redundancyResult.findings());

        return new MutationOutcome(mutation, List.copyOf(findings), List.copyOf(warnings),
            List.copyOf(incompleteReasons));
    }

    /**
     * {@code --file-coverage}: one {@link FileCoverageEntry} per already-
     * filtered file (same dataset {@code coverage.overall} is computed from,
     * hard rule 4), each file's own {@link MetricSet} computed by the same
     * {@link MetricsEngine} the headline numbers use - never a client-side
     * recomputation (Plan.md Faz 1).
     */
    private static FileCoverageBlock buildFileCoverageBlock(ExclusionFilter.Partition partition) {
        List<FileCoverageEntry> entries = partition.kept().stream()
            .map(f -> new FileCoverageEntry(f.moduleId(), f.repoRelativePath(),
                MetricsEngine.compute(List.of(f)), f.lines()))
            .toList();
        return new FileCoverageBlock(entries, partition.excludedPaths());
    }

    /**
     * D-64: progress goes to stderr, never stdout. stdout carries the text
     * report and must stay exactly what {@link TextRenderer} produced;
     * progress is transient status about a run that can take half an hour,
     * which is what a pipe or a redirect should drop. Always on - a caller
     * who does not want it redirects stderr, and the alternative (a run
     * that prints nothing for thirty minutes and then reports a timeout)
     * is what made the WTA dogfood undiagnosable.
     */
    private EvidenceDiagnostics buildDiagnostics() {
        java.io.PrintWriter err = spec.commandLine().getErr();
        java.util.function.Consumer<String> sink = message -> {
            err.println("proof-java: " + message);
            err.flush(); // a progress line is worthless if it only appears once the run ends
        };
        if (diagnosticsDirOption == null || diagnosticsDirOption.isBlank()) {
            return EvidenceDiagnostics.progressOnly(sink);
        }
        return new EvidenceDiagnostics(Path.of(diagnosticsDirOption), diagnosticsLevel(), sink);
    }

    /** @throws CliUsageException on an unknown level - a typo must not silently pick a verbosity. */
    private EvidenceDiagnostics.Level diagnosticsLevel() {
        if ("errors".equalsIgnoreCase(diagnosticsLevelOption)) {
            return EvidenceDiagnostics.Level.ERRORS;
        }
        if ("verbose".equalsIgnoreCase(diagnosticsLevelOption)) {
            return EvidenceDiagnostics.Level.VERBOSE;
        }
        throw new CliUsageException("--diagnostics-level must be 'errors' or 'verbose', got: "
            + diagnosticsLevelOption);
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
