package dev.proofjava.analysis.pertest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.proofjava.analysis.binding.SourceRootClassIndex;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * {@code --per-test-target <id>=<FQCN>} (Faz 14a): resolves each user-named
 * class to a PIT-glob via {@link SourceRootClassIndex}, the same pattern
 * {@code analysis.mutation.MutationTargetResolver} already established for
 * {@code --mutation-target} - L2 per-test evidence had no diff-free
 * entry point until this landed ({@link PerTestCollector} could previously
 * only target classes found via {@code ChangedClassTargets}, so inspecting
 * "which test covers this line" for an unmodified file required an
 * artificial, uncommitted edit just to make the file show up in a diff).
 *
 * <p>All-or-nothing at the whole-invocation level, same as the mutation
 * target resolver: {@code AnalyzeCommand} calls this only when at least one
 * {@code --per-test-target} was given, and every module's diff-derived
 * targets are then ignored entirely for L2, including modules with no
 * explicit target of their own.
 */
public final class PerTestTargetResolver {

    private PerTestTargetResolver() {
    }

    public record Result(Map<String, List<String>> targetGlobsById, List<AnalysisReason> warnings) {
    }

    public static Result resolve(Path repoRoot, List<ModuleDefinition> modules, Map<String, List<String>> targetFqcnsById) {
        Map<String, List<String>> globsById = new LinkedHashMap<>();
        List<AnalysisReason> warnings = new ArrayList<>();

        for (ModuleDefinition module : modules) {
            List<String> fqcns = targetFqcnsById.getOrDefault(module.id(), List.of());
            if (fqcns.isEmpty()) {
                // Distinct from a requested-but-unresolved target below (hard
                // rule 3a): this module was simply never named on any
                // --per-test-target, a normal shape for a multi-module run
                // targeting one module's class.
                warnings.add(new AnalysisReason("PER_TEST_TARGET_NOT_BOUND",
                    "Module '" + module.id() + "' was declared but has no --per-test-target bound to it; "
                        + "excluded from this run's per-test evidence.", null, module.id()));
                continue;
            }
            resolveModule(repoRoot, module, fqcns, globsById, warnings);
        }
        return new Result(globsById, warnings);
    }

    private static void resolveModule(Path repoRoot, ModuleDefinition module, List<String> fqcns,
                                       Map<String, List<String>> globsById, List<AnalysisReason> warnings) {
        List<String> globs = new ArrayList<>();
        for (String fqcn : fqcns) {
            SourceRootClassIndex.Resolution resolution = SourceRootClassIndex.resolve(repoRoot, module, fqcn);
            if (resolution.foundOnDisk()) {
                globs.add(fqcn + "*"); // trailing '*' also matches nested classes (Foo$Inner), same convention as ChangedClassTargets.globsFor
            } else {
                warnings.add(new AnalysisReason("PER_TEST_TARGET_UNRESOLVED",
                    "Module '" + module.id() + "' --per-test-target '" + fqcn + "' did not resolve to a source "
                        + "file under any declared source root; skipped.", null, module.id()));
            }
        }
        if (!globs.isEmpty()) {
            globsById.put(module.id(), globs);
        }
    }
}
