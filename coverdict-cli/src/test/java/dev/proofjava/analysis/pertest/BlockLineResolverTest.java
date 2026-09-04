package dev.proofjava.analysis.pertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.pitest.classinfo.ClassName;
import org.pitest.coverage.BlockCoverage;
import org.pitest.coverage.BlockLocation;
import org.pitest.coverage.LineMap;
import org.pitest.mutationtest.engine.Location;

/** Real PIT is never invoked here - {@link LineMap} is faked with a fixed block-to-line table. */
class BlockLineResolverTest {

    private static final ClassName CLASS = ClassName.fromString("com.example.Calc");

    private static BlockCoverage block(String method, int blockNumber, String... tests) {
        Location location = Location.location(CLASS, method, "()V");
        BlockLocation loc = BlockLocation.blockLocation(location, blockNumber);
        return new BlockCoverage(loc, List.of(tests));
    }

    @Test
    void twoBlocksOnTheSameLineDeduplicateTheSameTestInsteadOfDoublingIt() {
        LineMap lineMap = className -> Map.of(
            BlockLocation.blockLocation(Location.location(CLASS, "add", "()V"), 0), Set.of(10),
            BlockLocation.blockLocation(Location.location(CLASS, "add", "()V"), 1), Set.of(10));
        List<BlockCoverage> coverage = List.of(
            block("add", 0, "CalcTest#addsTwoNumbers"),
            block("add", 1, "CalcTest#addsTwoNumbers"));

        BlockLineResolver.Result result = BlockLineResolver.resolve("app", coverage, lineMap);

        assertEquals(1, result.evidence().entries().size());
        PerTestEntry entry = result.evidence().entries().get(0);
        assertEquals(1, entry.lines().size());
        assertEquals(List.of("CalcTest#addsTwoNumbers"), entry.lines().get(0).tests(),
            "the same test hitting the same line via two blocks must appear once, not twice");
    }

    @Test
    void clinitBlocksGoToTheAmbientBucketNotEntries() {
        LineMap lineMap = className -> Map.of(
            BlockLocation.blockLocation(Location.location(CLASS, "<clinit>", "()V"), 0), Set.of(5),
            BlockLocation.blockLocation(Location.location(CLASS, "add", "()V"), 0), Set.of(10));
        List<BlockCoverage> coverage = List.of(
            block("<clinit>", 0, "CalcTest#staticSetup"),
            block("add", 0, "CalcTest#addsTwoNumbers"));

        BlockLineResolver.Result result = BlockLineResolver.resolve("app", coverage, lineMap);

        assertEquals(1, result.evidence().entries().size());
        assertEquals("add", result.evidence().entries().get(0).methodName());
        assertEquals(1, result.evidence().ambient().size());
        assertEquals("<clinit>", result.evidence().ambient().get(0).methodName());
    }

    @Test
    void aBlockWithNoResolvedLinesIsSilentlySkipped() {
        LineMap lineMap = className -> Map.of(); // LineMapper found nothing for this class
        List<BlockCoverage> coverage = List.of(block("add", 0, "CalcTest#addsTwoNumbers"));

        BlockLineResolver.Result result = BlockLineResolver.resolve("app", coverage, lineMap);

        assertTrue(result.evidence().entries().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void entriesAndLinesAreSortedDeterministically() {
        LineMap lineMap = className -> Map.of(
            BlockLocation.blockLocation(Location.location(CLASS, "subtract", "()V"), 0), Set.of(20),
            BlockLocation.blockLocation(Location.location(CLASS, "add", "()V"), 0), Set.of(15, 10));
        List<BlockCoverage> coverage = List.of(
            block("subtract", 0, "CalcTest#subtracts"),
            block("add", 0, "CalcTest#addsTwoNumbers"));

        BlockLineResolver.Result result = BlockLineResolver.resolve("app", coverage, lineMap);

        List<PerTestEntry> entries = result.evidence().entries();
        assertEquals(2, entries.size());
        assertEquals("add", entries.get(0).methodName(), "add sorts before subtract");
        assertEquals("subtract", entries.get(1).methodName());
        assertEquals(List.of(10, 15), entries.get(0).lines().stream().map(PerTestLine::line).toList(),
            "lines within one entry must be ascending");
    }

    @Test
    void exceedingTheLineRecordCapDropsAllEvidenceForThatModuleWithAWarning() {
        Map<BlockLocation, Set<Integer>> manyLines = new java.util.HashMap<>();
        Location location = Location.location(CLASS, "add", "()V");
        for (int i = 0; i < BlockLineResolver.MAX_LINE_RECORDS + 1; i++) {
            manyLines.put(BlockLocation.blockLocation(location, i), Set.of(i));
        }
        LineMap lineMap = className -> manyLines;
        List<BlockCoverage> coverage = manyLines.keySet().stream()
            .map(loc -> new BlockCoverage(loc, List.of("CalcTest#addsTwoNumbers")))
            .toList();

        BlockLineResolver.Result result = BlockLineResolver.resolve("app", coverage, lineMap);

        assertTrue(result.evidence().entries().isEmpty(), "over-cap evidence is dropped entirely, never partially");
        assertEquals(1, result.warnings().size());
        assertEquals("PER_TEST_TRUNCATED", result.warnings().get(0).code());
    }
}
