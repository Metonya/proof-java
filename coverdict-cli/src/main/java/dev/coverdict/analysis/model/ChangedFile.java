package dev.coverdict.analysis.model;

import java.util.List;

/**
 * One entry of the verdict JSON's {@code changedFiles} array (schema {@code
 * $defs/changedFile}). {@code module} is {@code null} only when {@code
 * classification} is {@link Classification#UNKNOWN} because the path is not
 * under any declared module root (D-27) - the schema's own {@code module}
 * field is optional for exactly this case, never written as {@code null}
 * (omitted instead, since its schema type has no null variant).
 * {@code newLines}/{@code coveredNewLines}/{@code uncoveredNewRanges} are
 * {@code null} for every classification except {@link Classification#MAPPED}.
 */
public record ChangedFile(
    String path,
    String module,
    Classification classification,
    Integer newLines,
    Integer coveredNewLines,
    List<LineRange> uncoveredNewRanges
) {
}
