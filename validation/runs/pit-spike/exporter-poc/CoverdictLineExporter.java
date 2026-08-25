package org.pitest.coverage.export.coverdictspike;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.coverage.BlockCoverage;
import org.pitest.coverage.BlockLocation;
import org.pitest.coverage.CoverageExporter;
import org.pitest.coverage.CoverageExporterFactory;
import org.pitest.coverage.LineMap;
import org.pitest.coverage.analysis.LineMapper;
import org.pitest.mutationtest.engine.Location;
import org.pitest.plugin.Feature;
import org.pitest.util.ResultOutputStrategy;

/**
 * M2 Faz 0 spike PoC: proves block-number-to-source-line resolution is
 * possible through PIT's own public SPI (CoverageExporterFactory,
 * LineMapper), producing a real line -> tests map that linecoverage.xml
 * alone does not give (it exposes block numbers, not line numbers).
 * Not shipped; validation/scripts/pit-spike.ps1 only.
 */
public final class CoverdictLineExporter implements CoverageExporterFactory {

    @Override
    public CoverageExporter create(final ResultOutputStrategy outputStrategy) {
        final ClassByteArraySource source = new ClasspathByteArraySource();
        final LineMap lineMapper = new LineMapper(source);
        return new LineResolvingExporter(outputStrategy, lineMapper);
    }

    @Override
    public Feature provides() {
        return Feature.named("coverdictspike").withOnByDefault(false)
                .withDescription("M2 Faz 0 spike: resolves PIT block coverage to source lines");
    }

    @Override
    public String description() {
        return "coverdict M2 spike line-level exporter";
    }

    private static final class ClasspathByteArraySource implements ClassByteArraySource {
        // recordCoverage() runs in the Maven/pitest-maven process, not the
        // coverage minion - that process's own classloader never has the SUT
        // on it, so getResourceAsStream() silently returns nothing for every
        // class (spike finding, run10-debug.log: classesWithEmptyLineMap ==
        // totalBlocks). Read .class bytes off disk instead, from the same
        // output directories PIT itself was pointed at.
        private final List<java.io.File> classDirs;

        ClasspathByteArraySource() {
            final String prop = System.getProperty("coverdictspike.classDirs", "");
            final List<java.io.File> dirs = new java.util.ArrayList<>();
            for (final String p : prop.split(java.io.File.pathSeparator)) {
                if (!p.isBlank()) {
                    dirs.add(new java.io.File(p));
                }
            }
            this.classDirs = dirs;
        }

        @Override
        public Optional<byte[]> getBytes(final String className) {
            final String relative = className.replace('.', java.io.File.separatorChar) + ".class";
            for (final java.io.File dir : this.classDirs) {
                final java.io.File candidate = new java.io.File(dir, relative);
                if (candidate.isFile()) {
                    try {
                        return Optional.of(java.nio.file.Files.readAllBytes(candidate.toPath()));
                    } catch (final IOException e) {
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        }
    }

    private static final class LineResolvingExporter implements CoverageExporter {
        private final ResultOutputStrategy outputStrategy;
        private final LineMap lineMapper;
        private final Map<ClassName, Map<BlockLocation, Set<Integer>>> lineMapCache = new HashMap<>();

        LineResolvingExporter(final ResultOutputStrategy outputStrategy, final LineMap lineMapper) {
            this.outputStrategy = outputStrategy;
            this.lineMapper = lineMapper;
        }

        @Override
        public void recordCoverage(final Collection<BlockCoverage> coverage) {
            // line -> "class#method" -> tests, so identical line numbers across
            // classes never collide; TreeMap keeps output deterministic for diffing.
            final Map<String, Map<Integer, List<String>>> byClass = new TreeMap<>();
            int totalBlocks = 0;
            int matchedBlocks = 0;
            int emptyMapClasses = 0;

            for (final BlockCoverage block : coverage) {
                totalBlocks++;
                final BlockLocation loc = block.getBlock();
                final Location location = loc.getLocation();
                final ClassName clazz = location.getClassName();
                final Map<BlockLocation, Set<Integer>> lines = lineMapCache
                        .computeIfAbsent(clazz, this.lineMapper::mapLines);
                if (lines.isEmpty()) {
                    emptyMapClasses++;
                }
                final Set<Integer> resolvedLines = lines.get(loc);
                if (resolvedLines == null || resolvedLines.isEmpty()) {
                    continue;
                }
                matchedBlocks++;
                final String key = clazz.asJavaName() + "#" + location.getMethodName();
                final Map<Integer, List<String>> perLine = byClass.computeIfAbsent(key,
                        k -> new TreeMap<>());
                for (final Integer line : resolvedLines) {
                    perLine.computeIfAbsent(line, l -> new java.util.ArrayList<>())
                            .addAll(block.getTests());
                }
            }

            System.err.println("[coverdictspike] totalBlocks=" + totalBlocks
                    + " matchedBlocks=" + matchedBlocks
                    + " classesWithEmptyLineMap=" + emptyMapClasses
                    + " classesSeen=" + lineMapCache.size());

            try (Writer w = this.outputStrategy.createWriterForFile("coverdict-line-tests.json")) {
                w.write("{\n");
                boolean firstClass = true;
                for (final Map.Entry<String, Map<Integer, List<String>>> classEntry : byClass.entrySet()) {
                    if (!firstClass) {
                        w.write(",\n");
                    }
                    firstClass = false;
                    w.write("  \"" + escape(classEntry.getKey()) + "\": {\n");
                    boolean firstLine = true;
                    for (final Map.Entry<Integer, List<String>> lineEntry : classEntry.getValue().entrySet()) {
                        if (!firstLine) {
                            w.write(",\n");
                        }
                        firstLine = false;
                        w.write("    \"" + lineEntry.getKey() + "\": [\n");
                        final List<String> tests = lineEntry.getValue();
                        for (int i = 0; i < tests.size(); i++) {
                            w.write("      \"" + escape(tests.get(i)) + "\"");
                            w.write(i < tests.size() - 1 ? ",\n" : "\n");
                        }
                        w.write("    ]");
                    }
                    w.write("\n  }");
                }
                w.write("\n}\n");
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static String escape(final String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
