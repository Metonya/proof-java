package dev.coverdict.analysis.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.coverdict.analysis.binding.ChangedClassTargets;
import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.ModuleDefinition;

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
            Map<String, String> classNameToPath = ChangedClassTargets.classNameToPath(module, changedFiles);
            PseudoTestedMethodRule.Result result = PseudoTestedMethodRule.evaluate(evidence.moduleId(), classNameToPath, evidence);
            findings.addAll(result.findings());
            warnings.addAll(result.warnings());
        }
        return new Result(List.copyOf(findings), List.copyOf(warnings));
    }

    public record Result(List<Finding> findings, List<AnalysisReason> warnings) {
    }
}
