package dev.coverdict.analysis.pertest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;

import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.coverage.BlockCoverage;
import org.pitest.coverage.CoverageExporter;
import org.pitest.coverage.CoverageExporterFactory;
import org.pitest.coverage.LineMap;
import org.pitest.coverage.analysis.LineMapper;
import org.pitest.plugin.Feature;
import org.pitest.util.ResultOutputStrategy;

/**
 * PIT {@link CoverageExporterFactory} SPI implementation (D-47): resolves
 * PIT's block coverage to source lines via {@link LineMapper} and writes
 * {@link PerTestJsonWriter}'s wire format to {@code coverdict-line-tests.json}
 * in the run's report directory. Registered via {@code META-INF/services/
 * org.pitest.coverage.CoverageExporterFactory}; only active when {@link
 * PerTestDriver} explicitly requests the {@code coverdictspike} feature -
 * never on by default, so an ordinary {@code mvn verify} run of a target
 * repo embedding coverdict is unaffected.
 *
 * <p>Runs in the same JVM that calls {@link org.pitest.mutationtest.tooling.EntryPoint#execute}
 * (the PIT "driver" process, not the coverage minion) - {@code
 * ClassByteArraySource} here reads class bytes off the classpath PIT itself
 * was pointed at (production classes; PIT's compiled test classes are
 * covered separately by its own bytecode-analysis, not by this source).
 */
public final class CoverdictLineExporter implements CoverageExporterFactory {

    static final String OUTPUT_FILE_NAME = "coverdict-line-tests.json";

    /** Set by {@link PerTestDriver} before calling {@code EntryPoint.execute} - the only channel available to an SPI-instantiated exporter. */
    static final String MODULE_ID_PROPERTY = "coverdict.pertest.moduleId";

    @Override
    public CoverageExporter create(ResultOutputStrategy outputStrategy) {
        return new LineResolvingExporter(outputStrategy);
    }

    @Override
    public Feature provides() {
        return Feature.named("coverdictspike").withOnByDefault(false)
            .withDescription("coverdict per-test line coverage exporter (D-47/D-55)");
    }

    @Override
    public String description() {
        return "coverdict per-test line coverage exporter";
    }

    private static final class LineResolvingExporter implements CoverageExporter {
        private final ResultOutputStrategy outputStrategy;

        LineResolvingExporter(ResultOutputStrategy outputStrategy) {
            this.outputStrategy = outputStrategy;
        }

        @Override
        public void recordCoverage(Collection<BlockCoverage> coverage) {
            ClassByteArraySource source = new org.pitest.classpath.ClassPathByteArraySource();
            LineMap lineMap = new LineMapper(source);
            String moduleId = System.getProperty(MODULE_ID_PROPERTY, "");
            BlockLineResolver.Result resolved = BlockLineResolver.resolve(moduleId, List.copyOf(coverage), lineMap);
            try (Writer w = outputStrategy.createWriterForFile(OUTPUT_FILE_NAME)) {
                PerTestJsonWriter.write(w, resolved.evidence());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
