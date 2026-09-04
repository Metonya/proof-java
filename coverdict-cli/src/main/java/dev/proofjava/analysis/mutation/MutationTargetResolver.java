package dev.proofjava.analysis.mutation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.proofjava.analysis.binding.SourceRootClassIndex;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * {@code --mutation-target <id>=<FQCN>} (Plan.md Faz 2): resolves each
 * user-named class to a repo-relative source path via {@link
 * SourceRootClassIndex}, producing exactly what {@link MutationCollector}
 * and {@link MutationRuleEngine}'s target-mode overloads need - a PIT-glob
 * per module and a {@code className -> path} index for {@code
 * PSEUDO_TESTED_METHOD} to anchor its finding on, without ever touching
 * {@code ChangedClassTargets}/a diff (D-12/D-51: diff-scoped mutation is not
 * the only entry point coverdict supports).
 *
 * <p>All-or-nothing at the whole-invocation level (D-66's config-modules
 * precedent): {@code AnalyzeCommand} calls this only when at least one
 * {@code --mutation-target} was given, and every module's diff-derived
 * targets are then ignored entirely, including modules with no explicit
 * target of their own - "target this class" is a deliberate, narrow ask,
 * not a filter layered on top of the diff.
 */
public final class MutationTargetResolver {

    private MutationTargetResolver() {
    }

    public record Result(Map<String, List<String>> targetGlobsById,
                          Map<String, Map<String, String>> classNameToPathByModuleId,
                          List<AnalysisReason> warnings) {
    }

    public static Result resolve(Path repoRoot, List<ModuleDefinition> modules, Map<String, List<String>> targetFqcnsById) {
        Map<String, List<String>> globsById = new LinkedHashMap<>();
        Map<String, Map<String, String>> classNameToPathByModuleId = new LinkedHashMap<>();
        List<AnalysisReason> warnings = new ArrayList<>();

        for (ModuleDefinition module : modules) {
            List<String> fqcns = targetFqcnsById.getOrDefault(module.id(), List.of());
            if (fqcns.isEmpty()) {
                // Distinct from a requested-but-unresolved target below (hard
                // rule 3a): this module was simply never named on any
                // --mutation-target, which is a normal, expected shape for a
                // multi-module run targeting one module's class.
                warnings.add(new AnalysisReason("MUTATION_TARGET_NOT_BOUND",
                    "Module '" + module.id() + "' was declared but has no --mutation-target bound to it; "
                        + "excluded from this run's mutation evidence.", null, module.id()));
                continue;
            }
            resolveModule(repoRoot, module, fqcns, globsById, classNameToPathByModuleId, warnings);
        }
        return new Result(globsById, classNameToPathByModuleId, warnings);
    }

    private static void resolveModule(Path repoRoot, ModuleDefinition module, List<String> fqcns,
                                       Map<String, List<String>> globsById,
                                       Map<String, Map<String, String>> classNameToPathByModuleId,
                                       List<AnalysisReason> warnings) {
        List<String> globs = new ArrayList<>();
        Map<String, String> classNameToPath = new LinkedHashMap<>();
        for (String fqcn : fqcns) {
            SourceRootClassIndex.Resolution resolution = SourceRootClassIndex.resolve(repoRoot, module, fqcn);
            if (resolution.foundOnDisk()) {
                globs.add(fqcn + "*"); // trailing '*' also matches nested classes (Foo$Inner), same convention as ChangedClassTargets.globsFor
                classNameToPath.put(fqcn, resolution.path());
            } else {
                warnings.add(new AnalysisReason("MUTATION_TARGET_UNRESOLVED",
                    "Module '" + module.id() + "' --mutation-target '" + fqcn + "' did not resolve to a source "
                        + "file under any declared source root; skipped.", null, module.id()));
            }
        }
        if (!globs.isEmpty()) {
            globsById.put(module.id(), globs);
            classNameToPathByModuleId.put(module.id(), classNameToPath);
        }
    }
}
