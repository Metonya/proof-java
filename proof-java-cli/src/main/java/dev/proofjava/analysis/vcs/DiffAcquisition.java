package dev.proofjava.analysis.vcs;

/**
 * Orchestrates {@link GitClient} into the two diff modes docs/INPUT-MODEL.md
 * defines (D-16 merge-base semantics). Both methods throw the same {@link
 * dev.proofjava.analysis.AnalysisException} codes {@link GitClient}'s
 * individual calls already throw ({@code UNRESOLVABLE_HEAD}, {@code
 * UNRESOLVABLE_REF}, {@code MISSING_MERGE_BASE}, {@code GIT_TIMEOUT}, {@code
 * GIT_INVOCATION_FAILED}) - there is nothing left to translate, only to
 * sequence: resolve identity first (cheap, and the likeliest failure point
 * for a bad {@code --base} ref), then the diff and untracked-file listing.
 */
public final class DiffAcquisition {

    private DiffAcquisition() {
    }

    /** Working-tree mode: {@code HEAD} to the working tree, staged and unstaged alike. */
    public static DiffResult acquireWorkingTree(GitClient git) {
        String head = git.resolveHead();
        boolean dirty = git.isDirty();
        return new DiffResult(
            new VcsIdentity(head, null, null, null, dirty),
            UnifiedDiffParser.parse(git.diffUnified0("HEAD")),
            git.untrackedFiles());
    }

    /** Base-ref mode: {@code merge-base(baseRef, HEAD)} to the working tree (committed work since the branch point plus uncommitted edits). */
    public static DiffResult acquireBaseRef(GitClient git, String baseRef) {
        String head = git.resolveHead();
        String base = git.resolveRef(baseRef);
        String mergeBase = git.mergeBase(base, head);
        boolean dirty = git.isDirty();
        return new DiffResult(
            new VcsIdentity(head, baseRef, base, mergeBase, dirty),
            UnifiedDiffParser.parse(git.diffUnified0(mergeBase)),
            git.untrackedFiles());
    }
}
