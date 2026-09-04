package dev.proofjava.analysis.binding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.ModuleDefinition;

/**
 * Diff-scoped FQCN globs for one module's changed production classes -
 * shared by every PIT-driving collector (L2 per-test coverage, L3 mutation).
 * Resolves O-05: PIT's own {@code scmMutationCoverage} goal was removed
 * (D-12), so this is proof-java's own git-diff-to-target mapping.
 */
public final class ChangedClassTargets {

    private ChangedClassTargets() {
    }

    /** FQCN globs (trailing {@code *} covers nested/inner classes, e.g. {@code Foo$Bar}) for {@code module}'s mapped changed {@code .java} files. */
    public static List<String> globsFor(ModuleDefinition module, List<ChangedFile> changedFiles) {
        List<String> globs = new ArrayList<>();
        forEachMappedFile(module, changedFiles, (className, path) -> globs.add(className + "*"));
        return globs;
    }

    /**
     * The reverse of {@link #globsFor}: top-level FQCN to repo-relative
     * source path, for a mutation finding that needs to report which file a
     * mutated method (possibly a nested class, {@code Outer$Inner}) came
     * from. Callers strip anything from the first {@code '$'} before
     * looking a mutated method's class name up in this index.
     */
    public static Map<String, String> classNameToPath(ModuleDefinition module, List<ChangedFile> changedFiles) {
        Map<String, String> index = new LinkedHashMap<>();
        forEachMappedFile(module, changedFiles, index::put);
        return index;
    }

    private static void forEachMappedFile(ModuleDefinition module, List<ChangedFile> changedFiles,
                                           java.util.function.BiConsumer<String, String> onClass) {
        for (ChangedFile cf : changedFiles) {
            if (cf.classification() != Classification.MAPPED || !module.id().equals(cf.module())
                    || !cf.path().endsWith(".java")) {
                continue;
            }
            for (String sourceRoot : module.sourceRoots()) {
                String prefix = sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/";
                if (cf.path().startsWith(prefix)) {
                    String relative = cf.path().substring(prefix.length(), cf.path().length() - ".java".length());
                    onClass.accept(relative.replace('/', '.'), cf.path());
                    break;
                }
            }
        }
    }
}
