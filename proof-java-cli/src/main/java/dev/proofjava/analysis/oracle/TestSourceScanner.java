package dev.proofjava.analysis.oracle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import dev.proofjava.analysis.AnalysisException;
import dev.proofjava.analysis.model.ModuleDefinition;
import dev.proofjava.analysis.model.RepoPaths;

/**
 * Walks every declared module's testRoots for .java files (docs/INPUT-MODEL.md).
 * A module whose test root does not exist on disk is not an error - plenty of
 * modules have no tests yet - it simply contributes no files.
 */
public final class TestSourceScanner {

    private TestSourceScanner() {
    }

    /**
     * @param changedPathsOrNull when non-null (K1's findings-scope=changed),
     *                           only paths in this set are kept - the caller
     *                           supplies the union of a diff's changed and
     *                           untracked paths.
     */
    public static List<TestSourceFile> scan(Path repoRoot, List<ModuleDefinition> modules, Set<String> changedPathsOrNull) {
        List<TestSourceFile> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ModuleDefinition module : modules) {
            for (String testRoot : module.testRoots()) {
                scanTestRoot(repoRoot, module, testRoot, changedPathsOrNull, seen, found);
            }
        }
        found.sort(Comparator.comparing(TestSourceFile::moduleId).thenComparing(TestSourceFile::repoRelativePath));
        return found;
    }

    private static void scanTestRoot(Path repoRoot, ModuleDefinition module, String testRoot, Set<String> changedPathsOrNull,
                                      Set<String> seen, List<TestSourceFile> found) {
        Path dir = repoRoot.resolve(testRoot);
        if (!Files.isDirectory(dir)) {
            return;
        }
        Path realRepoRoot = realPathOrNull(repoRoot);
        for (Path absolute : listJavaFiles(dir)) {
            // Files.walk does not follow directory symlinks, but a regular
            // file itself can be a symlink pointing outside the repo -
            // resolve and check before it is ever parsed (SECURITY-POLICY.md
            // #4, M1c criterion 7).
            Path real = realPathOrNull(absolute);
            if (realRepoRoot != null && real != null && !real.startsWith(realRepoRoot)) {
                continue;
            }
            String relative = RepoPaths.normalizeSeparators(repoRoot.relativize(absolute).toString());
            boolean inScope = changedPathsOrNull == null || changedPathsOrNull.contains(relative);
            if (inScope && seen.add(module.id() + " " + relative)) {
                found.add(new TestSourceFile(module.id(), relative, absolute));
            }
        }
    }

    private static Path realPathOrNull(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    private static List<Path> listJavaFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java")).toList();
        } catch (IOException | UncheckedIOException e) {
            throw new AnalysisException("TEST_SOURCE_SCAN_FAILED", "Could not walk test source root '" + dir + "': " + e.getMessage());
        }
    }
}
