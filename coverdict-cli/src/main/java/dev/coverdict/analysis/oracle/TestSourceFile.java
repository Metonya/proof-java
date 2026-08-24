package dev.coverdict.analysis.oracle;

import java.nio.file.Path;

/** One test source file found under a module's declared testRoots (docs/M0-CLI-INPUT.md), ready to parse. */
public record TestSourceFile(String moduleId, String repoRelativePath, Path absolutePath) {
}
