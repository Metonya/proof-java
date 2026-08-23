package dev.coverdict.analysis.vcs;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Everything {@link DiffAcquisition} gathers for one analysis run: the
 * resolved identity, the changed new-side line numbers per repo-relative
 * path (already forward-slash normalized by {@link UnifiedDiffParser}), and
 * every untracked path (D-16: an untracked Java file can make a run
 * incomplete on its own; every other untracked path is only a warning -
 * that classification happens downstream, this record just carries the raw
 * list).
 */
public record DiffResult(VcsIdentity identity, Map<String, SortedSet<Integer>> changedLinesByPath, List<String> untrackedFiles) {
}
