package dev.coverdict.analysis.pertest;

import java.util.List;

/** One source line's covering tests (schema {@code $defs/perTestLine}), 1-based, tests sorted lexicographically. */
public record PerTestLine(int line, List<String> tests) {
}
