package dev.coverdict.analysis.pertest;

import java.util.List;

/**
 * One module's L2 evidence (schema {@code $defs/perTestModule|D-46}: never a
 * finding source on its own). {@code entries} is ordinary per-instance code;
 * {@code ambient} is D-50's static-initializer bucket ({@code <clinit>}
 * only), never test-attributable.
 */
public record PerTestModuleEvidence(String moduleId, List<PerTestEntry> entries, List<PerTestEntry> ambient) {
}
