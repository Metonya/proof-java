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

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import dev.coverdict.analysis.AnalysisException;
import dev.coverdict.analysis.binding.BindingResult;
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
import dev.coverdict.analysis.report.ModuleInput;
import dev.coverdict.analysis.report.ReportInput;
import dev.coverdict.analysis.report.TextRenderer;
import dev.coverdict.analysis.report.ToolVersion;
import dev.coverdict.analysis.report.VerdictDocument;
import dev.coverdict.analysis.report.VerdictJsonWriter;

/**
 * This build supports only {@code --no-vcs} (docs/M0-CLI-INPUT.md): overall
 * coverage in all three metric modes, no changed-code/new-code numbers (base
 * ref and working-tree diff modes are the next ROADMAP M1a step).
 *
 * <p>Two distinct failure shapes, matching the schema's own note that "exit
 * codes 2 and 4 abort before a verdict document is written": a
 * {@link CliUsageException} (bad option syntax) never writes JSON and exits
 * 2; an {@link AnalysisException} (bad evidence - malformed XML, an
 * undeclared module, a duplicate class identity) always writes a structured
 * incomplete verdict and exits 3 (hard rule 3a).
 */
@Command(name = "analyze", description = "Analyze coverage and test-oracle evidence for a repository.")
class AnalyzeCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--no-vcs", description = "Overall coverage only; no changed-code numbers. The only mode this build supports.")
    private boolean noVcs;

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

    @Option(names = "--language-level", defaultValue = "17", description = "Recorded as provenance; nothing in this build parses source syntax yet.")
    private int languageLevel;

    @Option(names = "--encoding", defaultValue = "UTF-8", description = "Recorded as provenance; nothing in this build reads source files yet.")
    private String encoding;

    @Option(names = "--coverage-exclusions", description = "Comma-separated sonar.coverage.exclusions globs, one list for the whole run (D-05).")
    private String exclusionsArg;

    @Option(names = "--out", defaultValue = "coverdict-verdict.json", description = "Verdict JSON output path.")
    private String outOption;

    @Override
    public Integer call() {
        if (!noVcs) {
            spec.commandLine().getErr().println(
                "coverdict: this build supports only --no-vcs; --base and --uncommitted "
                + "diff modes are the next ROADMAP M1a step (docs/M0-CLI-INPUT.md).");
            return ExitCode.INVALID_INPUT.value();
        }

        Path repoRoot = Path.of(repoOption != null ? repoOption : System.getProperty("user.dir"));
        List<String> exclusions = exclusionsArg == null || exclusionsArg.isBlank()
            ? List.of()
            : Arrays.stream(exclusionsArg.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();

        List<ModuleDefinition> modules;
        Map<String, List<String>> reportPathsById;
        try {
            reportPathsById = parseReportArgs();
            modules = buildModuleDefinitions(reportPathsById.keySet());
        } catch (CliUsageException e) {
            spec.commandLine().getErr().println("coverdict: " + e.getMessage());
            return ExitCode.INVALID_INPUT.value(); // no JSON written - invocation itself was invalid
        }

        VerdictDocument doc;
        try {
            doc = analyze(repoRoot, exclusions, modules, reportPathsById);
        } catch (AnalysisException e) {
            doc = incompleteDocument(exclusions, e);
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

    /** @throws AnalysisException when the evidence itself is bad (exit 3, structured incomplete document). */
    private VerdictDocument analyze(Path repoRoot, List<String> exclusions, List<ModuleDefinition> modules,
                                     Map<String, List<String>> reportPathsById) {
        List<AnalysisReason> extraWarnings = new ArrayList<>();

        // A declared module with no bound report has no coverage evidence in
        // --no-vcs mode (M0-CLI-INPUT.md: this is only a hard error when the
        // module has changed Java files, a diff-mode concept this build
        // doesn't have yet) - warn and leave it out of the analyzed set,
        // rather than passing an empty-evidence module through.
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

        BindingResult binding = new ModuleBinder(repoRoot).bind(evidencedModules, parsedReportsById);
        List<ResolvedSourceFile> filtered = ExclusionFilter.apply(binding.resolvedFiles(), exclusions);
        MetricSet overall = MetricsEngine.computeOverall(filtered);

        List<ModuleInput> moduleInputs = evidencedModules.stream()
            .map(m -> new ModuleInput(m.id(), m.root(), m.sourceRoots(), m.testRoots(), reportInputsById.get(m.id())))
            .toList();

        List<AnalysisReason> warnings = new ArrayList<>(extraWarnings);
        warnings.addAll(binding.warnings());

        ToolVersion.Info version = ToolVersion.read();
        return new VerdictDocument(
            version.schemaVersion(), version.version(), true, List.of(),
            languageLevel, encoding, exclusions, moduleInputs, overall, warnings);
    }

    private VerdictDocument incompleteDocument(List<String> exclusions, AnalysisException e) {
        ToolVersion.Info version = ToolVersion.read();
        return new VerdictDocument(
            version.schemaVersion(), version.version(), false,
            List.of(new AnalysisReason(e.code(), e.getMessage())),
            languageLevel, encoding, exclusions, List.of(),
            MetricsEngine.computeOverall(List.of()), List.of());
    }

    private static Map<String, String> parseIdValue(List<String> args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq < 0) {
                throw new CliUsageException("Expected <id>=<value>, got: " + arg);
            }
            result.put(arg.substring(0, eq), arg.substring(eq + 1));
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
