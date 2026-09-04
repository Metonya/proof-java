package dev.proofjava.analysis.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.proofjava.analysis.binding.ChangedClassTargets;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Finding;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * Public entry point for M5's L3 rules over collected mutation evidence -
 * {@code OracleRuleEngine}'s sibling, one method deep instead of iterating
 * AST-derived test methods. Currently runs a single rule ({@link
 * PseudoTestedMethodRule}); a second L3 rule would slot in here the same
 * way {@code OracleRuleEngine.scanFile} composes its four.
 */
public final class MutationRuleEngine {

    private MutationRuleEngine() {
    }

    public static Result evaluate(List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                   List<MutationModuleEvidence> moduleEvidence) {
        Map<String, Map<String, String>> classNameToPathByModuleId = new java.util.HashMap<>();
        for (ModuleDefinition module : modules) {
            classNameToPathByModuleId.put(module.id(), ChangedClassTargets.classNameToPath(module, changedFiles));
        }
        return evaluate(modules, classNameToPathByModuleId, moduleEvidence);
    }

    /**
     * {@code --mutation-target} (Plan.md Faz 2): {@code classNameToPathByModuleId}
     * comes from {@link MutationTargetResolver} instead of {@link
     * ChangedClassTargets} - no diff is consulted. A module id present in
     * {@code moduleEvidence} but absent from this map (defensive - {@link
     * MutationCollector#collectForTargets} only ever produces evidence for a
     * module it also resolved targets for) is treated the same as an empty
     * index: every finding for that module's methods is skipped rather than
     * guessed at (hard rule 3a), same as an unresolvable class name today.
     */
    public static Result evaluate(List<ModuleDefinition> modules, Map<String, Map<String, String>> classNameToPathByModuleId,
                                   List<MutationModuleEvidence> moduleEvidence) {
        Map<String, ModuleDefinition> modulesById = new java.util.HashMap<>();
        for (ModuleDefinition module : modules) {
            modulesById.put(module.id(), module);
        }

        List<Finding> findings = new ArrayList<>();
        List<AnalysisReason> warnings = new ArrayList<>();
        for (MutationModuleEvidence evidence : moduleEvidence) {
            ModuleDefinition module = modulesById.get(evidence.moduleId());
            if (module == null) {
                continue; // evidence for a module id not in the declared set - defensive, should not occur
            }
            Map<String, String> classNameToPath = classNameToPathByModuleId.getOrDefault(evidence.moduleId(), Map.of());
            PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate(evidence.moduleId(), classNameToPath, evidence);
            findings.addAll(result.findings());
            warnings.addAll(result.warnings());
        }
        return new Result(List.copyOf(findings), List.copyOf(warnings));
    }

    public record Result(List<Finding> findings, List<AnalysisReason> warnings) {
    }
}
