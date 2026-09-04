package dev.proofjava.analysis.report;

import java.util.List;

/**
 * Schema {@code fileCoverage} (Plan.md Faz 1, {@code --file-coverage}, opt-in
 * - D-46/D-55/D-56's "absent, never null unless requested" pattern). {@code
 * excluded} lists the repo-relative paths {@link
 * dev.proofjava.analysis.metrics.ExclusionFilter} removed from the filtered
 * dataset, so an IDE surface can show a file as explicitly excluded rather
 * than "unknown" or "uncovered" (hard rule 3a).
 */
public record FileCoverageBlock(List<FileCoverageEntry> files, List<String> excluded) {
}
