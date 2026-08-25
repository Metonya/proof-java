package dev.coverdict.analysis.pertest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.ChangedFile;
import dev.coverdict.analysis.model.Classification;
import dev.coverdict.analysis.model.ModuleDefinition;

/**
 * D-46's evidence-layer orchestrator: runs {@link PerTestRunner} once per
 * module whose changed files include mapped production code, and never lets
 * a module's failure - a missing classpath, an unreadable bytecode version
 * (D-53), a timeout, any PIT-internal error - abort the run. Every failure
 * becomes a warning (hard rule 3a: absent, never silently green) and that
 * module's evidence is simply missing from {@code perTest}, exactly like
 * {@code MODULE_WITHOUT_REPORT} degrades L1 evidence one module at a time.
 */
public final class PerTestCollector {

    private PerTestCollector() {
    }

    public static Result collect(Path repoRoot, List<ModuleDefinition> modules, List<ChangedFile> changedFiles,
                                  Map<String, String> perTestClasspathFilesById) {
        List<PerTestModuleEvidence> evidence = new ArrayList<>();
        List<AnalysisReason> warnings = new ArrayList<>();

        for (ModuleDefinition module : modules) {
            List<String> targetClasses = targetClassGlobs(module, changedFiles);
            if (targetClasses.isEmpty()) {
                continue; // nothing changed in this module's production code - no evidence to collect
            }
            String classpathFile = perTestClasspathFilesById.get(module.id());
            if (classpathFile == null) {
                warnings.add(new AnalysisReason("PER_TEST_CLASSPATH_MISSING",
                    "Module '" + module.id() + "' has changed production classes but no --per-test-classpath "
                        + "bound to it; per-test evidence skipped for this module.", null, module.id()));
                continue;
            }

            PerTestClasspathLoader.Result classpath = PerTestClasspathLoader.load(repoRoot, module.id(), classpathFile);
            if (!classpath.warnings().isEmpty()) {
                warnings.addAll(classpath.warnings());
                continue;
            }

            try {
                Optional<PerTestModuleEvidence> result = PerTestRunner.run(module.id(), repoRoot,
                    classpath.classPathElements(), classpath.codePaths(), targetClasses);
                result.ifPresent(evidence::add);
            } catch (PerTestCollectionException e) {
                warnings.add(new AnalysisReason("PER_TEST_COLLECTION_FAILED",
                    "Module '" + module.id() + "' per-test coverage collection failed (" + e.getMessage()
                        + "); per-test evidence skipped for this module.", null, module.id()));
            }
        }

        return new Result(List.copyOf(evidence), List.copyOf(warnings));
    }

    private static List<String> targetClassGlobs(ModuleDefinition module, List<ChangedFile> changedFiles) {
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
                    globs.add(relative.replace('/', '.') + "*"); // '*' covers nested/inner classes (Foo$Bar)
                    break;
                }
            }
        }
        return globs;
    }

    public record Result(List<PerTestModuleEvidence> modules, List<AnalysisReason> warnings) {
    }
}
