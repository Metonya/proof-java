package dev.coverdict.analysis.redundancy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;

/**
 * D-61's public entry point - {@code MutationRuleEngine}'s sibling, run
 * alongside it whenever {@code --mutation-report} is set (mutation evidence
 * is the only precondition; unlike D-46's superseded three-stage design,
 * neither {@code --per-test-report} nor an oracle scan is required). Only
 * needs {@code repoRoot} beyond what {@code MutationRuleEngine.evaluate}
 * already takes - {@link SubsumedTestRule}'s test-path resolution reads the
 * module's test roots directly off disk rather than through an already-built
 * changed-files index (subsumed/dominator tests are not necessarily changed
 * files).
 */
public final class RedundancyRuleEngine {

    private RedundancyRuleEngine() {
    }

    public static Result evaluate(Path repoRoot, List<ModuleDefinition> modules,
                                   List<MutationModuleEvidence> moduleEvidence) {
        Map<String, ModuleDefinition> modulesById = new HashMap<>();
        for (ModuleDefinition module : modules) {
            modulesById.put(module.id(), module);
        }

        List<Finding> findings = new ArrayList<>();
        for (MutationModuleEvidence evidence : moduleEvidence) {
            ModuleDefinition module = modulesById.get(evidence.moduleId());
            if (module == null) {
                continue; // evidence for a module id not in the declared set - defensive, should not occur
            }
            findings.addAll(SubsumedTestRule.evaluate(repoRoot, module, evidence));
        }
        return new Result(List.copyOf(findings));
    }

    public record Result(List<Finding> findings) {
    }
}
