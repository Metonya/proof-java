package dev.coverdict.analysis.pertest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.coverdict.analysis.binding.ChangedClassTargets;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.subprocess.EvidenceDiagnostics;

/**
 * D-46's evidence-layer orchestrator: runs {@link PerTestRunner} once per
 * module whose changed files include mapped production code, and never lets
 * a module's failure - a missing classpath, an unreadable bytecode version
 * (D-53), a timeout, any PIT-internal error - abort the run. Every failure
 * becomes a warning (hard rule 3a: absent, never silently green) and that
 * module's evidence is simply missing from {@code perTest}, exactly like
 * {@code MODULE_WITHOUT_REPORT} degrades L1 evidence one module at a time.
 */
public final class PerTestCollector {

    private static final String MODULE_PREFIX = "Module '";

    private PerTestCollector() {
    }

    public static Result collect(Path repoRoot, List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                  Map<String, String> perTestClasspathFilesById) {
        return collect(repoRoot, modules, changedFiles, perTestClasspathFilesById, EvidenceDiagnostics.none());
    }

    public static Result collect(Path repoRoot, List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                  Map<String, String> perTestClasspathFilesById, EvidenceDiagnostics diagnostics) {
        Accumulator acc = new Accumulator();

        for (ModuleDefinition module : modules) {
            List<String> targetClasses = ChangedClassTargets.globsFor(module, changedFiles);
            AnalysisReason noTargetsReason = new AnalysisReason("PER_TEST_NO_CHANGED_TARGETS",
                MODULE_PREFIX + module.id() + "' has no mapped changed production class, so no per-test evidence "
                    + "was requested from the engine.", null, module.id(), 0);
            collectOneModule(repoRoot, module, targetClasses, noTargetsReason, perTestClasspathFilesById, acc, diagnostics);
        }

        return acc.toResult();
    }

    /**
     * {@code --per-test-target} (Faz 14a): target classes come from {@link
     * PerTestTargetResolver}, never {@link ChangedClassTargets} - no diff is
     * consulted at all, which is what lets {@code --per-test-target} work
     * under {@code --no-vcs}. A module absent from {@code targetGlobsById}
     * is never re-warned here - {@link PerTestTargetResolver} already
     * explained exactly why (not bound at all, vs. bound but every class
     * unresolved), so a second, less specific warning would only duplicate it.
     */
    public static Result collectForTargets(Path repoRoot, List<ModuleDefinition> modules,
                                            Map<String, List<String>> targetGlobsById,
                                            Map<String, String> perTestClasspathFilesById,
                                            EvidenceDiagnostics diagnostics) {
        Accumulator acc = new Accumulator();

        for (ModuleDefinition module : modules) {
            List<String> targetClasses = targetGlobsById.getOrDefault(module.id(), List.of());
            collectOneModule(repoRoot, module, targetClasses, null, perTestClasspathFilesById, acc, diagnostics);
        }

        return acc.toResult();
    }

    /**
     * One module's collection attempt (SonarQube java:S135 - {@link
     * #collect} stays continue-free). {@code noTargetsReason} may be
     * {@code null} when the caller (target mode) already explained an empty
     * target list itself. {@code acc} bundles the two accumulator lists
     * (SonarQube java:S107 - collect/collectForTargets/collectOneModule
     * always mutate evidence and warnings together, so one parameter object
     * is both the fix and the more accurate shape).
     */
    private static void collectOneModule(Path repoRoot, ModuleDefinition module, List<String> targetClasses,
                                          AnalysisReason noTargetsReason, Map<String, String> perTestClasspathFilesById,
                                          Accumulator acc, EvidenceDiagnostics diagnostics) {
        if (targetClasses.isEmpty()) {
            // D-64: this used to be a silent return. --per-test-report was
            // explicitly asked for, so "this module contributed nothing"
            // is a fact the user needs, not an implementation detail -
            // WTA's first run had every module land here (HEAD == the base
            // ref, so nothing had changed) and the verdict explained none
            // of it.
            if (noTargetsReason != null) {
                acc.warnings.add(noTargetsReason);
            }
            return;
        }
        diagnostics.progress("per-test: module '" + module.id() + "' - " + targetClasses.size() + " target class(es)");
        String classpathFile = perTestClasspathFilesById.get(module.id());
        if (classpathFile == null) {
            acc.warnings.add(new AnalysisReason("PER_TEST_CLASSPATH_MISSING",
                MODULE_PREFIX + module.id() + "' has changed production classes but no --per-test-classpath "
                    + "bound to it; per-test evidence skipped for this module.", null, module.id()));
            return;
        }

        PerTestClasspathLoader.Result classpath = PerTestClasspathLoader.load(repoRoot, module.id(), classpathFile);
        if (!classpath.warnings().isEmpty()) {
            acc.warnings.addAll(classpath.warnings());
            return;
        }

        try {
            Optional<PerTestModuleEvidence> result = PerTestRunner.run(module.id(), repoRoot,
                classpath.classPathElements(), classpath.codePaths(), targetClasses, diagnostics);
            result.ifPresent(one -> recordEvidence(module, one, acc));
        } catch (PerTestCollectionException e) {
            acc.warnings.add(new AnalysisReason("PER_TEST_COLLECTION_FAILED",
                MODULE_PREFIX + module.id() + "' per-test coverage collection failed (" + e.getMessage()
                    + "); per-test evidence skipped for this module.", null, module.id()));
        }
    }

    /**
     * D-64: evidence that came back structurally valid but carrying zero
     * records is still missing evidence (hard rule 3a). WTA's L2 run
     * reported {@code entries: []} for two modules with no warning at all,
     * which read as success; the module is still published so the shape of
     * the run stays visible, but the emptiness is now named.
     */
    private static void recordEvidence(ModuleDefinition module, PerTestModuleEvidence one, Accumulator acc) {
        acc.evidence.add(one);
        if (one.entries().isEmpty() && one.ambient().isEmpty()) {
            acc.warnings.add(new AnalysisReason("PER_TEST_EMPTY_EVIDENCE",
                MODULE_PREFIX + module.id() + "' per-test coverage ran but resolved no test-to-line record; "
                    + "no per-test evidence is available for it despite being requested.", null, module.id(), 0));
        }
    }

    private static final class Accumulator {
        private final List<PerTestModuleEvidence> evidence = new ArrayList<>();
        private final List<AnalysisReason> warnings = new ArrayList<>();

        Result toResult() {
            return new Result(List.copyOf(evidence), List.copyOf(warnings));
        }
    }

    public record Result(List<PerTestModuleEvidence> modules, List<AnalysisReason> warnings) {
    }
}
