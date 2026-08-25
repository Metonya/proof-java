package dev.coverdict.analysis.mutation;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.coverdict.analysis.binding.ChangedClassTargets;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * D-56's evidence-layer orchestrator: runs {@link MutationRunner} once per
 * module whose changed files include mapped production code - {@link
 * dev.coverdict.analysis.pertest.PerTestCollector}'s L3 sibling, same
 * never-abort contract (hard rule 3a: a module's failure becomes a warning,
 * evidence is simply missing from {@code mutation}, the run itself never
 * fails over it).
 */
public final class MutationCollector {

    private MutationCollector() {
    }

    public static Result collect(Path repoRoot, List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                  Map<String, String> mutationClasspathFilesById, Duration budget) {
        List<MutationModuleEvidence> evidence = new ArrayList<>();
        List<AnalysisReason> warnings = new ArrayList<>();

        for (ModuleDefinition module : modules) {
            collectOneModule(repoRoot, module, changedFiles, mutationClasspathFilesById, budget, evidence, warnings);
        }

        return new Result(List.copyOf(evidence), List.copyOf(warnings));
    }

    /** One module's collection attempt (SonarQube java:S135 - {@link #collect} stays continue-free). */
    private static void collectOneModule(Path repoRoot, ModuleDefinition module, List<ChangedFile> changedFiles,
                                          Map<String, String> mutationClasspathFilesById, Duration budget,
                                          List<MutationModuleEvidence> evidence, List<AnalysisReason> warnings) {
        List<String> targetClasses = ChangedClassTargets.globsFor(module, changedFiles);
        if (targetClasses.isEmpty()) {
            return; // nothing changed in this module's production code - no evidence to collect
        }
        String classpathFile = mutationClasspathFilesById.get(module.id());
        if (classpathFile == null) {
            warnings.add(new AnalysisReason("MUTATION_CLASSPATH_MISSING",
                "Module '" + module.id() + "' has changed production classes but no --mutation-classpath "
                    + "bound to it; mutation evidence skipped for this module.", null, module.id()));
            return;
        }

        MutationClasspathLoader.Result classpath = MutationClasspathLoader.load(repoRoot, module.id(), classpathFile);
        if (!classpath.warnings().isEmpty()) {
            warnings.addAll(classpath.warnings());
            return;
        }

        try {
            Optional<MutationModuleEvidence> result = MutationRunner.run(module.id(), repoRoot,
                classpath.classPathElements(), classpath.codePaths(), targetClasses, budget);
            result.ifPresent(one -> {
                evidence.add(one);
                warnings.addAll(one.warnings());
            });
        } catch (MutationCollectionException e) {
            warnings.add(new AnalysisReason(e.reasonCode(),
                "Module '" + module.id() + "' mutation evidence collection failed (" + e.getMessage()
                    + "); mutation evidence skipped for this module.", null, module.id()));
        }
    }

    public record Result(List<MutationModuleEvidence> modules, List<AnalysisReason> warnings) {
    }
}
