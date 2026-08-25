package dev.coverdict.analysis.mutation;

import java.util.List;

import dev.coverdict.analysis.model.AnalysisReason;

/**
 * One module's L3 evidence (schema {@code $defs/mutationModule}). {@code
 * warnings} travels in this same wire record - unlike L2's {@code
 * PerTestModuleEvidence}, whose {@link
 * dev.coverdict.analysis.pertest.BlockLineResolver} warnings never reach the
 * parent process (a gap found while building this). A degraded or
 * truncated evidence set for this module is still visible to {@code
 * MutationCollector} (hard rule 3a: absent, never silently green).
 */
public record MutationModuleEvidence(String moduleId, List<MutatedMethod> methods, List<AnalysisReason> warnings) {
}
