package dev.coverdict.analysis.pertest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.pitest.classinfo.ClassName;
import org.pitest.coverage.BlockCoverage;
import org.pitest.coverage.BlockLocation;
import org.pitest.coverage.LineMap;
import org.pitest.mutationtest.engine.Location;

import dev.coverdict.analysis.model.AnalysisReason;

/**
 * Resolves PIT's block-indexed {@link BlockCoverage} into coverdict's
 * line-indexed {@link PerTestEntry} shape via {@link LineMap} (D-47/D-49:
 * {@code linecoverage.xml}'s own {@code block} numbers are not source
 * lines). A source line can carry the same test twice across two blocks
 * (multi-to-one, Faz 0 spike finding) - the per-line test set is
 * deduplicated through a {@link LinkedHashSet} before being frozen and
 * sorted into the record, unlike the spike PoC's plain {@link List}.
 *
 * <p>{@code TreeMap} keys are {@code "<className>#<methodName>"}: Java class
 * and method names never contain {@code '#'}, so string order over the
 * combined key is exactly the schema's {@code (className, methodName)}
 * ordering rule.
 */
public final class BlockLineResolver {

    /**
     * SECURITY-POLICY.md #2 precedent ({@code ClasspathLoader.MAX_JAR_ENTRIES}):
     * PIT's own coverage phase is a trusted in-process run, not attacker
     * input, but a pathological target should still degrade to a warning
     * rather than an unbounded JSON. assertj-core's own 215-class slice
     * produced ~40,000 line records (FINDINGS.md Faz 2b) - comfortably
     * inside real per-module scope. Truncation here is all-or-nothing
     * (never a partial cut of the tail): a diff-scoped run's evidence is
     * either fully reported or dropped with a count, never silently uneven.
     */
    static final int MAX_LINE_RECORDS = 200_000;

    private static final String CLINIT = "<clinit>";

    private BlockLineResolver() {
    }

    public static Result resolve(String moduleId, List<BlockCoverage> coverage, LineMap lineMap) {
        Map<ClassName, Map<BlockLocation, Set<Integer>>> lineCache = new LinkedHashMap<>();
        Map<String, Map<Integer, Set<String>>> regular = new TreeMap<>();
        Map<String, Map<Integer, Set<String>>> ambient = new TreeMap<>();

        for (BlockCoverage block : coverage) {
            accumulate(block, lineMap, lineCache, regular, ambient);
        }

        List<PerTestEntry> entries = toEntries(regular);
        List<PerTestEntry> ambientEntries = toEntries(ambient);
        int totalLines = countLines(entries) + countLines(ambientEntries);

        if (totalLines > MAX_LINE_RECORDS) {
            AnalysisReason truncated = new AnalysisReason("PER_TEST_TRUNCATED",
                "Module '" + moduleId + "' per-test evidence carries " + totalLines + " line records, over the "
                    + MAX_LINE_RECORDS + " limit (SECURITY-POLICY.md #2); dropped rather than partially reported.",
                null, moduleId, totalLines);
            return new Result(new PerTestModuleEvidence(moduleId, List.of(), List.of()), List.of(truncated));
        }
        return new Result(new PerTestModuleEvidence(moduleId, entries, ambientEntries), List.of());
    }

    private static void accumulate(BlockCoverage block, LineMap lineMap,
                                    Map<ClassName, Map<BlockLocation, Set<Integer>>> lineCache,
                                    Map<String, Map<Integer, Set<String>>> regular,
                                    Map<String, Map<Integer, Set<String>>> ambient) {
        BlockLocation loc = block.getBlock();
        Location location = loc.getLocation();
        ClassName className = location.getClassName();
        Map<BlockLocation, Set<Integer>> classLines = lineCache.computeIfAbsent(className, lineMap::mapLines);
        Set<Integer> resolvedLines = classLines.get(loc);
        if (resolvedLines == null || resolvedLines.isEmpty()) {
            return;
        }
        boolean isAmbient = CLINIT.equals(location.getMethodName());
        Map<String, Map<Integer, Set<String>>> target = isAmbient ? ambient : regular;
        String key = className.asJavaName() + "#" + location.getMethodName();
        Map<Integer, Set<String>> perLine = target.computeIfAbsent(key, k -> new TreeMap<>());
        for (Integer line : resolvedLines) {
            perLine.computeIfAbsent(line, l -> new LinkedHashSet<>()).addAll(block.getTests());
        }
    }

    private static List<PerTestEntry> toEntries(Map<String, Map<Integer, Set<String>>> byKey) {
        List<PerTestEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Set<String>>> classEntry : byKey.entrySet()) {
            int hashIndex = classEntry.getKey().indexOf('#');
            String className = classEntry.getKey().substring(0, hashIndex);
            String methodName = classEntry.getKey().substring(hashIndex + 1);
            List<PerTestLine> lines = new ArrayList<>();
            for (Map.Entry<Integer, Set<String>> lineEntry : classEntry.getValue().entrySet()) {
                List<String> tests = new ArrayList<>(lineEntry.getValue());
                Collections.sort(tests);
                lines.add(new PerTestLine(lineEntry.getKey(), List.copyOf(tests)));
            }
            entries.add(new PerTestEntry(className, methodName, List.copyOf(lines)));
        }
        return entries;
    }

    private static int countLines(List<PerTestEntry> entries) {
        int total = 0;
        for (PerTestEntry entry : entries) {
            total += entry.lines().size();
        }
        return total;
    }

    public record Result(PerTestModuleEvidence evidence, List<AnalysisReason> warnings) {
    }
}
