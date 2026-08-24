package dev.coverdict.analysis.model;

/** One closed, 1-indexed line range (schema {@code changedFile.uncoveredNewRanges} entry): {@code [start, end]}, inclusive. */
public record LineRange(int start, int end) {
}
