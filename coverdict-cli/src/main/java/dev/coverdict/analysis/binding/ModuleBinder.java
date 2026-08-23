package dev.coverdict.analysis.binding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.coverdict.analysis.AnalysisException;
import dev.coverdict.analysis.jacoco.JacocoReport;
import dev.coverdict.analysis.jacoco.SourceFileReport;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.ResolvedSourceFile;

/**
 * Resolves each JaCoCo {@code <sourcefile>} to a repo-relative path under its
 * module and rejects reports that disagree about the same class identity
 * (D-16: "overlapping class identities are rejected rather than
 * counter-merged"), rather than silently summing or overwriting them.
 */
public final class ModuleBinder {

    private final Path repoRoot;

    public ModuleBinder(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    /**
     * @param modules            every module declared on the CLI
     * @param reportsByModuleId  parsed JaCoCo reports, keyed by the module id
     *                           they were bound to with {@code --report id=path}
     */
    public BindingResult bind(List<ModuleDefinition> modules, Map<String, List<JacocoReport>> reportsByModuleId) {
        Map<String, ModuleDefinition> byId = new LinkedHashMap<>();
        for (ModuleDefinition m : modules) {
            byId.put(m.id(), m);
        }
        for (String reportModuleId : reportsByModuleId.keySet()) {
            if (!byId.containsKey(reportModuleId)) {
                throw new AnalysisException("UNDECLARED_MODULE",
                    "A --report was bound to module '" + reportModuleId + "', which was never declared with --module.");
            }
        }

        List<ResolvedSourceFile> resolved = new ArrayList<>();
        List<AnalysisReason> warnings = new ArrayList<>();

        for (ModuleDefinition module : modules) {
            // repoRelativePath -> which report (by index) first claimed it,
            // so a later report claiming the same path can be reported as a
            // rejected overlap rather than silently overwriting.
            Map<String, Integer> claimedBy = new LinkedHashMap<>();
            List<JacocoReport> reports = reportsByModuleId.getOrDefault(module.id(), List.of());

            for (int reportIndex = 0; reportIndex < reports.size(); reportIndex++) {
                JacocoReport report = reports.get(reportIndex);
                for (SourceFileReport sf : report.sourceFiles()) {
                    ResolvedPath resolvedPath = resolvePath(module, sf.packageQualifiedPath());
                    if (!resolvedPath.foundOnDisk()) {
                        warnings.add(new AnalysisReason("MISSING_SOURCE_FILE",
                            "Coverage reported for '" + resolvedPath.path()
                                + "' but the file was not found on disk under any declared source root.",
                            resolvedPath.path(), module.id()));
                    }

                    Integer previousReportIndex = claimedBy.get(resolvedPath.path());
                    if (previousReportIndex != null && previousReportIndex != reportIndex) {
                        throw new AnalysisException("DUPLICATE_CLASS_IDENTITY",
                            "'" + resolvedPath.path() + "' in module '" + module.id()
                                + "' is reported by more than one JaCoCo XML file; overlapping class"
                                + " identities are rejected rather than counter-merged (D-16).");
                    }
                    claimedBy.put(resolvedPath.path(), reportIndex);

                    resolved.add(new ResolvedSourceFile(module.id(), resolvedPath.path(), sf.lines(),
                        sf.reportedLineMissed(), sf.reportedLineCovered(), resolvedPath.foundOnDisk()));
                }
            }
        }

        return new BindingResult(resolved, warnings);
    }

    private ResolvedPath resolvePath(ModuleDefinition module, String packageQualifiedPath) {
        List<String> sourceRoots = module.sourceRoots().isEmpty() ? List.of(module.root()) : module.sourceRoots();
        for (String sourceRoot : sourceRoots) {
            String candidate = join(sourceRoot, packageQualifiedPath);
            if (Files.exists(repoRoot.resolve(candidate))) {
                return new ResolvedPath(candidate, true);
            }
        }
        return new ResolvedPath(join(sourceRoots.get(0), packageQualifiedPath), false);
    }

    private static String join(String base, String suffix) {
        if (base == null || base.isEmpty() || base.equals(".")) {
            return suffix;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/" + suffix;
    }

    private record ResolvedPath(String path, boolean foundOnDisk) {
    }
}
