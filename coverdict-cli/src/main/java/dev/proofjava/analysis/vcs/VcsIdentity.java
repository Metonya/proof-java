package dev.proofjava.analysis.vcs;

/**
 * D-16's identity record: the resolved commit(s) a diff was computed
 * against, and whether the working tree has uncommitted tracked changes.
 * {@code baseRef}/{@code base}/{@code mergeBase} are all {@code null} in
 * working-tree mode (schema {@code inputs.resolved}: "base and mergeBase
 * present only in base-ref mode").
 */
public record VcsIdentity(String head, String baseRef, String base, String mergeBase, boolean dirty) {
}
