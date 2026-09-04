package dev.proofjava.analysis.model;

import java.util.List;

/**
 * A module as declared on the CLI (docs/INPUT-MODEL.md): a stable id plus
 * repo-relative, forward-slash-normalized paths. {@code sourceRoots} are
 * already full repo-relative paths (e.g. {@code "proof-java-cli/src/main/java"},
 * matching the documented default {@code <module-root>/src/main/java}) - not
 * fragments relative to {@code root} that would need a second join.
 */
public record ModuleDefinition(String id, String root, List<String> sourceRoots, List<String> testRoots) {
}
