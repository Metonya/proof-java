package dev.proofjava.analysis.oracle;

import java.nio.file.Path;

/** One test source file found under a module's declared testRoots (docs/INPUT-MODEL.md), ready to parse. */
public record TestSourceFile(String moduleId, String repoRelativePath, Path absolutePath) {
}
