package dev.proofjava.analysis.mutation;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.proofjava.analysis.binding.ChangedClassTargets;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.ModuleDefinition;
import dev.proofjava.analysis.subprocess.EvidenceDiagnostics;
import dev.proofjava.analysis.subprocess.PitJdkSupport;

/**
 * D-56's evidence-layer orchestrator: runs {@link MutationRunner} once per
 * module whose changed files include mapped production code - {@link
 * dev.proofjava.analysis.pertest.PerTestCollector}'s L3 sibling, same
 * never-abort contract (hard rule 3a: a module's failure becomes a warning,
 * evidence is simply missing from {@code mutation}, the run itself never
 * fails over it).
 */
public final class MutationCollector {

    private static final String MODULE_PREFIX = "Module '";

    private MutationCollector() {
    }

    public static Result collect(Path repoRoot, List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                  Map<String, String> mutationClasspathFilesById, Duration budget) {
        return collect(repoRoot, modules, changedFiles, mutationClasspathFilesById, budget,
            EvidenceDiagnostics.none());
    }

    public static Result collect(Path repoRoot, List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                  Map<String, String> mutationClasspathFilesById, Duration budget,
                                  EvidenceDiagnostics diagnostics) {
        Context context = new Context(repoRoot, mutationClasspathFilesById, budget, diagnostics, new Accumulator());

        for (ModuleDefinition module : modules) {
            List<String> targetClasses = ChangedClassTargets.globsFor(module, changedFiles);
            AnalysisReason noTargetsReason = new AnalysisReason("MUTATION_NO_CHANGED_TARGETS",
                MODULE_PREFIX + module.id() + "' has no mapped changed production class, so no mutation evidence "
                    + "was requested from the engine.", null, module.id(), 0);
            collectOneModule(module, targetClasses, noTargetsReason, context);
        }

        return context.acc().toResult();
    }

    /**
     * {@code --mutation-target} (Plan.md Faz 2): target classes come from
     * {@link MutationTargetResolver}, never {@link ChangedClassTargets} - no
     * diff is consulted at all, which is what lets {@code --mutation-target}
     * work under {@code --no-vcs}. A module absent from {@code
     * targetGlobsById} is never re-warned here - {@link MutationTargetResolver}
     * already explained exactly why (not bound at all, vs. bound but every
     * class unresolved), so a second, less specific warning would only
     * duplicate it.
     */
    public static Result collectForTargets(Path repoRoot, List<ModuleDefinition> modules,
                                            Map<String, List<String>> targetGlobsById,
                                            Map<String, String> mutationClasspathFilesById, Duration budget,
                                            EvidenceDiagnostics diagnostics) {
        Context context = new Context(repoRoot, mutationClasspathFilesById, budget, diagnostics, new Accumulator());

        for (ModuleDefinition module : modules) {
            List<String> targetClasses = targetGlobsById.getOrDefault(module.id(), List.of());
            collectOneModule(module, targetClasses, null, context);
        }

        return context.acc().toResult();
    }

    /**
     * Everything one {@link #collect}/{@link #collectForTargets} call shares
     * across every module it iterates (SonarQube java:S107 - the same
     * object for every module in one run, unlike {@code module}/{@code
     * targetClasses}/{@code noTargetsReason}, which is what stayed out of
     * this record - mirrors {@code PerTestCollector}'s identical shape).
     */
    private record Context(Path repoRoot, Map<String, String> mutationClasspathFilesById, Duration budget,
                            EvidenceDiagnostics diagnostics, Accumulator acc) {
    }

    /** One module's collection attempt (SonarQube java:S135 - {@link #collect} stays continue-free). {@code noTargetsReason} may be {@code null} when the caller already explained an empty target list itself. */
    private static void collectOneModule(ModuleDefinition module, List<String> targetClasses,
                                          AnalysisReason noTargetsReason, Context context) {
        if (targetClasses.isEmpty()) {
            // D-64: see PerTestCollector - a silent return here is exactly
            // how WTA's first --mutation-report run produced an empty
            // mutation block with a "complete" verdict and no explanation.
            if (noTargetsReason != null) {
                context.acc().warnings.add(noTargetsReason);
            }
            return;
        }
        context.diagnostics().progress("mutation: module '" + module.id() + "' - " + targetClasses.size()
            + " target class(es), budget " + context.budget().toSeconds() + "s");
        String classpathFile = context.mutationClasspathFilesById().get(module.id());
        if (classpathFile == null) {
            // D-88: the same reasoning as D-85's collection failure. The user
            // asked for mutation evidence and none exists for this module, and
            // whether that is a timeout or a forgotten flag does not change what
            // the result is missing. A configuration mistake is easier to see
            // than a runtime one, but "easy to see" was the argument for leaving
            // the timeout a warning too, and it was not enough.
            context.acc().incompleteReasons.add(new AnalysisReason("MUTATION_CLASSPATH_MISSING",
                MODULE_PREFIX + module.id() + "' has target classes to mutate but no --mutation-classpath "
                    + "bound to it; mutation evidence skipped for this module.", null, module.id()));
            return;
        }

        MutationClasspathLoader.Result classpath = MutationClasspathLoader.load(context.repoRoot(), module.id(), classpathFile);
        if (!classpath.warnings().isEmpty()) {
            context.acc().incompleteReasons.addAll(classpath.warnings());
            return;
        }

        try {
            Optional<MutationModuleEvidence> result = MutationRunner.run(module.id(), context.repoRoot(),
                classpath.classPathElements(), classpath.codePaths(), targetClasses, context.budget(), context.diagnostics());
            result.ifPresent(one -> recordEvidence(module, one, context.acc()));
        } catch (MutationCollectionException e) {
            // D-85: --mutation-report was asked for and did not deliver what it
            // promised. That is missing evidence, not a side note - as a warning
            // it left the run reporting "complete" with an empty mutation block,
            // the same silent-green shape the unsupported-JDK gate fixed.
            String hint = PitJdkSupport.isClasspathBytecodeCrash(e.getMessage())
                ? PitJdkSupport.classpathBytecodeHint() : "";
            context.acc().incompleteReasons.add(new AnalysisReason(e.reasonCode(),
                MODULE_PREFIX + module.id() + "' mutation evidence collection failed (" + e.getMessage()
                    + "); mutation evidence for this module is partial or absent." + hint, null, module.id()));
            // Whatever it did measure is still real evidence and still worth
            // reporting - the run is incomplete either way.
            e.partialEvidence().ifPresent(partial -> {
                context.acc().evidence.add(partial);
                context.acc().warnings.addAll(partial.warnings());
            });
        }
    }

    /** D-64: structurally valid evidence carrying no mutated method is still missing evidence (hard rule 3a), same as L2's {@code PER_TEST_EMPTY_EVIDENCE}. */
    private static void recordEvidence(ModuleDefinition module, MutationModuleEvidence one, Accumulator acc) {
        acc.evidence.add(one);
        acc.warnings.addAll(one.warnings());
        if (one.methods().isEmpty()) {
            acc.warnings.add(new AnalysisReason("MUTATION_EMPTY_EVIDENCE",
                MODULE_PREFIX + module.id() + "' mutation run produced no mutated method; no mutation evidence is "
                    + "available for it despite being requested.", null, module.id(), 0));
        }
    }

    private static final class Accumulator {
        private final List<MutationModuleEvidence> evidence = new ArrayList<>();
        private final List<AnalysisReason> warnings = new ArrayList<>();
        private final List<AnalysisReason> incompleteReasons = new ArrayList<>();

        Result toResult() {
            return new Result(List.copyOf(evidence), List.copyOf(warnings), List.copyOf(incompleteReasons));
        }
    }

    public record Result(List<MutationModuleEvidence> modules, List<AnalysisReason> warnings,
                         List<AnalysisReason> incompleteReasons) {
    }
}
