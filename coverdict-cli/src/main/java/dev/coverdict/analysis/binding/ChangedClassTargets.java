package dev.coverdict.analysis.binding;

import java.util.ArrayList;
import java.util.List;

import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * Diff-scoped FQCN globs for one module's changed production classes -
 * shared by every PIT-driving collector (L2 per-test coverage, L3 mutation).
 * Resolves O-05: PIT's own {@code scmMutationCoverage} goal was removed
 * (D-12), so this is coverdict's own git-diff-to-target mapping.
 */
public final class ChangedClassTargets {

    private ChangedClassTargets() {
    }

    /** FQCN globs (trailing {@code *} covers nested/inner classes, e.g. {@code Foo$Bar}) for {@code module}'s mapped changed {@code .java} files. */
    public static List<String> globsFor(ModuleDefinition module, List<ChangedFile> changedFiles) {
        List<String> globs = new ArrayList<>();
        for (ChangedFile cf : changedFiles) {
            if (cf.classification() != Classification.MAPPED || !module.id().equals(cf.module())
                    || !cf.path().endsWith(".java")) {
                continue;
            }
            for (String sourceRoot : module.sourceRoots()) {
                String prefix = sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/";
                if (cf.path().startsWith(prefix)) {
                    String relative = cf.path().substring(prefix.length(), cf.path().length() - ".java".length());
                    globs.add(relative.replace('/', '.') + "*");
                    break;
                }
            }
        }
        return globs;
    }
}
