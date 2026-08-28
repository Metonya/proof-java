package dev.coverdict.analysis.binding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.RepoPaths;

/**
 * FQCN to repo-relative source path, for an explicitly-named class target
 * ({@code --mutation-target}/{@code --per-test-target}, Plan.md Faz 2 /
 * Faz 14): unlike {@link ChangedClassTargets}, which reads the path
 * straight out of an already-classified {@code ChangedFile}, this resolves
 * a user-supplied class name against a module's {@code sourceRoots()}
 * directly - there is no diff involved. Applies the same repo-escape guard
 * {@code ModuleBinder.resolvePath} applies to a JaCoCo XML path, since a
 * target value is equally capable of naming a class outside the repo.
 * Never throws: an unresolved target is reported by the caller (hard rule
 * 3a), not rejected as an invalid invocation - the id itself was already
 * validated against a declared module. Public (moved out of {@code
 * analysis.mutation}, Faz 14a) so both the mutation and per-test explicit-
 * target resolvers can share it without duplicating the path math.
 */
public final class SourceRootClassIndex {

    private SourceRootClassIndex() {
    }

    public record Resolution(String path, boolean foundOnDisk) {
        public static final Resolution UNRESOLVED = new Resolution(null, false);
    }

    public static Resolution resolve(Path repoRoot, ModuleDefinition module, String fqcn) {
        String outer = stripNestedSuffix(fqcn);
        String relative = outer.replace('.', '/') + ".java";
        if (RepoPaths.isEscapingRepoRoot(relative)) {
            return Resolution.UNRESOLVED; // never join an escaping path with a source root (SECURITY-POLICY.md #4)
        }
        List<String> sourceRoots = module.sourceRoots().isEmpty() ? List.of(module.root()) : module.sourceRoots();
        for (String sourceRoot : sourceRoots) {
            String candidate = RepoPaths.join(sourceRoot, relative);
            if (Files.exists(repoRoot.resolve(candidate))) {
                return new Resolution(candidate, true);
            }
        }
        return Resolution.UNRESOLVED;
    }

    /** Strips a nested-class suffix ({@code Outer$Inner} -> {@code Outer}) - only the top-level class has its own source file. */
    private static String stripNestedSuffix(String className) {
        int dollar = className.indexOf('$');
        return dollar < 0 ? className : className.substring(0, dollar);
    }
}
