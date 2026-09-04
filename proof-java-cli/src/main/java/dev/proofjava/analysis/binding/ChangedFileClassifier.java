package dev.proofjava.analysis.binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import dev.proofjava.analysis.jacoco.LineCoverage;
import dev.proofjava.analysis.metrics.ExclusionFilter;
import dev.proofjava.analysis.model.AnalysisReason;
import dev.proofjava.analysis.model.ChangedFile;
import dev.proofjava.analysis.model.Classification;
import dev.proofjava.analysis.model.LineRange;
import dev.proofjava.analysis.model.ModuleDefinition;
import dev.proofjava.analysis.model.ResolvedSourceFile;

/**
 * Classifies every changed path from a {@link dev.proofjava.analysis.vcs.DiffResult}
 * into exactly one of the five buckets docs/INPUT-MODEL.md requires (D-27).
 * First match wins, checked in this order for a reason:
 *
 * <ol>
 *   <li>Not a {@code .java}/{@code .kt}/{@code .scala} path - out of this
 *       tool's domain entirely (a changed README, pom.xml, ...), no
 *       {@code changedFiles} entry at all.</li>
 *   <li>No declared module root contains the path (longest root wins for
 *       nested modules) - {@code unknown}, {@code module} omitted (the
 *       schema has no valid value for it here), and the run goes
 *       incomplete: {@code changedFile.module} is required to have SOME
 *       value normally, so a path outside every module is the one case
 *       genuinely unrepresentable any other way.</li>
 *   <li>{@code .kt}/{@code .scala} - {@code unsupported} (O-07); still
 *       attached to its owning module, since that part IS known.</li>
 *   <li>Matches {@code --coverage-exclusions}, or sits under the module's
 *       own {@code testRoots} - {@code excluded}. A changed test file is not
 *       missing evidence (JaCoCo does not report test code by design); it is
 *       simply out of the coverage measurement's scope.</li>
 *   <li>Present in a report bound to this module - {@code mapped} (with real
 *       new-line/covered-new-line/uncovered-range data), or {@code
 *       non-executable} if the report itself lists zero lines for it.</li>
 *   <li>{@code package-info.java}/{@code module-info.java} by name - {@code
 *       non-executable}. Checked only as a fallback AFTER the report lookup,
 *       because {@code module-info.java} sometimes does appear in a real
 *       report; treating the name as authoritative first would silently
 *       drop real coverage data for it.</li>
 *   <li>Otherwise - {@code unknown}: a source-rooted Java path with no
 *       report entry and no name-based excuse. Most likely a stale report
 *       (built before this file existed or grew); the run goes incomplete
 *       rather than silently reporting 100% or 0% new-code coverage for it
 *       (hard rule 3a).</li>
 * </ol>
 *
 * <p>A changed file that IS mapped but only PARTIALLY present in the report
 * (some of its changed lines are executable-in-the-diff but absent from the
 * report's own line list - the same "stale report" cause, just not total)
 * is not itself made incomplete: the numbers computed from what the report
 * does know are real, just an optimistic upper bound. That gap is disclosed
 * once, in aggregate, as the {@code CHANGED_LINES_ABSENT_FROM_REPORT}
 * warning - counting per-file mtimes to guess staleness was considered and
 * rejected (D-26): it would depend on checkout/rebuild timing, breaking
 * byte-determinism across runs of the exact same commit.
 */
public final class ChangedFileClassifier {

    private ChangedFileClassifier() {
    }

    public static ClassificationResult classify(
            Map<String, SortedSet<Integer>> changedLinesByPath,
            List<String> untrackedFiles,
            List<ModuleDefinition> modules,
            List<String> exclusionGlobs,
            List<ResolvedSourceFile> boundFiles) {

        List<Pattern> exclusionPatterns = ExclusionFilter.compile(exclusionGlobs);
        Map<String, ResolvedSourceFile> boundByModuleAndPath = indexByModuleAndPath(boundFiles);
        Accumulator acc = new Accumulator();

        for (String path : new TreeSet<>(changedLinesByPath.keySet())) {
            classifyOne(path, changedLinesByPath.get(path), modules, exclusionPatterns, boundByModuleAndPath, acc);
        }

        classifyUntracked(untrackedFiles, modules, exclusionPatterns, acc);

        if (acc.excluded > 0) {
            acc.warnings.add(new AnalysisReason("CHANGED_FILES_EXCLUDED",
                acc.excluded + " changed file(s) excluded from new-code coverage by --coverage-exclusions or a test root."));
        }
        if (acc.staleFiles > 0) {
            acc.warnings.add(new AnalysisReason("CHANGED_LINES_ABSENT_FROM_REPORT",
                acc.staleLines + " changed line(s) across " + acc.staleFiles + " file(s) are absent from the "
                    + "bound report(s) and excluded from new-code numerators/denominators; the report may be older than this diff."));
        }

        acc.changedFiles.sort(Comparator.comparing(ChangedFile::module, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ChangedFile::path));
        return new ClassificationResult(acc.changedFiles, acc.newCodeDataset, acc.incompleteReasons, acc.warnings);
    }

    private static void classifyOne(String path, SortedSet<Integer> changedNumbers, List<ModuleDefinition> modules,
                                     List<Pattern> exclusionPatterns, Map<String, ResolvedSourceFile> boundByModuleAndPath,
                                     Accumulator acc) {
        String ext = extension(path);
        boolean isJava = ".java".equals(ext);
        boolean isUnsupportedJvm = ".kt".equals(ext) || ".scala".equals(ext);
        if (!isJava && !isUnsupportedJvm) {
            return; // not a JVM source path this tool covers at all
        }

        ModuleDefinition module = resolveOwningModule(path, modules);
        if (module == null) {
            acc.changedFiles.add(new ChangedFile(path, null, Classification.UNKNOWN, null, null, List.of()));
            acc.incompleteReasons.add(new AnalysisReason("CHANGED_JAVA_OUTSIDE_MODULES",
                "Changed path '" + path + "' is not under any declared module root.", path));
            return;
        }

        if (isUnsupportedJvm) {
            acc.changedFiles.add(new ChangedFile(path, module.id(), Classification.UNSUPPORTED, null, null, List.of()));
            return;
        }

        if (ExclusionFilter.matchesAny(path, exclusionPatterns) || underAnyTestRoot(path, module)) {
            acc.changedFiles.add(new ChangedFile(path, module.id(), Classification.EXCLUDED, null, null, List.of()));
            acc.excluded++;
            return;
        }

        ResolvedSourceFile bound = boundByModuleAndPath.get(key(module.id(), path));
        if (bound != null) {
            addMapped(path, module.id(), changedNumbers, bound, acc);
            return;
        }

        if (isPackageOrModuleInfo(path)) {
            acc.changedFiles.add(new ChangedFile(path, module.id(), Classification.NON_EXECUTABLE, null, null, List.of()));
            return;
        }

        acc.changedFiles.add(new ChangedFile(path, module.id(), Classification.UNKNOWN, null, null, List.of()));
        acc.incompleteReasons.add(new AnalysisReason("REPORT_MISSING_CHANGED_FILE",
            "Changed path '" + path + "' is under module '" + module.id() + "' but absent from every bound report; "
                + "the report may be older than this diff. Rebuild coverage and rerun.", path, module.id()));
    }

    private static void addMapped(String path, String moduleId, SortedSet<Integer> changedNumbers, ResolvedSourceFile bound, Accumulator acc) {
        if (bound.lines().isEmpty()) {
            acc.changedFiles.add(new ChangedFile(path, moduleId, Classification.NON_EXECUTABLE, null, null, List.of()));
            return;
        }
        List<LineCoverage> executableChanged = new ArrayList<>();
        for (LineCoverage line : bound.lines()) {
            if (changedNumbers.contains(line.number())) {
                executableChanged.add(line);
            }
        }
        int newLines = executableChanged.size();
        int coveredNewLines = 0;
        List<Integer> uncoveredNumbers = new ArrayList<>();
        for (LineCoverage line : executableChanged) {
            if (line.isCovered()) {
                coveredNewLines++;
            } else {
                uncoveredNumbers.add(line.number());
            }
        }
        Collections.sort(uncoveredNumbers);
        acc.changedFiles.add(new ChangedFile(path, moduleId, Classification.MAPPED, newLines, coveredNewLines, coalesce(uncoveredNumbers)));
        // Same executableChanged list the numbers above came from - MetricsEngine.compute
        // over this dataset is guaranteed to agree with the per-file sums (hard rule 4).
        acc.newCodeDataset.add(new ResolvedSourceFile(moduleId, path, executableChanged, 0, 0, true));

        if (newLines < changedNumbers.size()) {
            acc.staleFiles++;
            acc.staleLines += changedNumbers.size() - newLines;
        }
    }

    private static void classifyUntracked(List<String> untrackedFiles, List<ModuleDefinition> modules,
                                           List<Pattern> exclusionPatterns, Accumulator acc) {
        for (String path : untrackedFiles) {
            if (!path.endsWith(".java")) {
                acc.warnings.add(new AnalysisReason("UNTRACKED_NON_JAVA_FILE", "Untracked file ignored by diff analysis.", path));
                continue;
            }
            ModuleDefinition module = resolveOwningModule(path, modules);
            boolean isTestFile = module != null && underAnyTestRoot(path, module);
            boolean isExcluded = ExclusionFilter.matchesAny(path, exclusionPatterns);
            if (!isTestFile && !isExcluded) {
                acc.incompleteReasons.add(new AnalysisReason("UNTRACKED_JAVA_FILE",
                    "Untracked Java file '" + path + "' is not tracked by git and was not analyzed (D-16).", path,
                    module == null ? null : module.id()));
            }
        }
    }

    private static Map<String, ResolvedSourceFile> indexByModuleAndPath(List<ResolvedSourceFile> boundFiles) {
        Map<String, ResolvedSourceFile> index = new HashMap<>();
        for (ResolvedSourceFile f : boundFiles) {
            index.put(key(f.moduleId(), f.repoRelativePath()), f);
        }
        return index;
    }

    /** NUL-separated: a module id can never contain one, unlike a space or "/" a real path could (D-22). */
    /** NUL-separated: a module id can never contain one, unlike a space or "/" a real path could (D-22). */
    private static String key(String moduleId, String path) {
        return moduleId + "\u0000" + path;
    }

    /** Longest declared module root under which {@code path} falls; {@code null} if none. A root of {@code "."} matches every path. */
    private static ModuleDefinition resolveOwningModule(String path, List<ModuleDefinition> modules) {
        ModuleDefinition best = null;
        int bestWeight = -1;
        for (ModuleDefinition module : modules) {
            String root = module.root();
            boolean isRootModule = root.isEmpty() || ".".equals(root);
            if (!isRootModule && !startsWithSegment(path, root)) {
                continue;
            }
            int weight = isRootModule ? 0 : root.length();
            if (weight > bestWeight) {
                best = module;
                bestWeight = weight;
            }
        }
        return best;
    }

    private static boolean underAnyTestRoot(String path, ModuleDefinition module) {
        for (String testRoot : module.testRoots()) {
            if (startsWithSegment(path, testRoot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithSegment(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean isPackageOrModuleInfo(String path) {
        return path.equals("package-info.java") || path.endsWith("/package-info.java")
            || path.equals("module-info.java") || path.endsWith("/module-info.java");
    }

    private static String extension(String path) {
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    /** Turns a sorted, deduplicated list of line numbers into closed, ascending, non-overlapping ranges (schema note on {@code uncoveredNewRanges}). */
    private static List<LineRange> coalesce(List<Integer> sortedNumbers) {
        List<LineRange> ranges = new ArrayList<>();
        int i = 0;
        while (i < sortedNumbers.size()) {
            int start = sortedNumbers.get(i);
            int end = start;
            int j = i + 1;
            while (j < sortedNumbers.size() && sortedNumbers.get(j) == end + 1) {
                end = sortedNumbers.get(j);
                j++;
            }
            ranges.add(new LineRange(start, end));
            i = j;
        }
        return ranges;
    }

    /** All the mutable state one {@link #classify} run threads through - grouped so no helper method needs more than a handful of parameters. */
    private static final class Accumulator {
        final List<ChangedFile> changedFiles = new ArrayList<>();
        final List<ResolvedSourceFile> newCodeDataset = new ArrayList<>();
        final List<AnalysisReason> incompleteReasons = new ArrayList<>();
        final List<AnalysisReason> warnings = new ArrayList<>();
        int excluded;
        int staleFiles;
        int staleLines;
    }
}
