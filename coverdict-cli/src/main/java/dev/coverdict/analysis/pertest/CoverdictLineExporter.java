package dev.coverdict.analysis.pertest;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassPath;
import org.pitest.classpath.ClassPathByteArraySource;
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
 * (the PIT "driver" process, not the coverage minion).
 *
 * <p><strong>D-68:</strong> {@code new ClassPathByteArraySource()}'s
 * no-arg constructor resolves class bytes through {@code
 * ClassPath.getClassPathElementsAsFiles()} - the running JVM's own {@code
 * java.class.path}, confirmed by disassembling the constructor. That is
 * never the target repo's classes: this driver JVM is launched with only
 * coverdict's own shaded jar on its {@code -cp} ({@code PerTestRunner}
 * only needs its own classes plus PIT's on that launch command - the
 * target classpath is handed to PIT separately, through {@code
 * ReportOptions}). The result before this fix: PIT's minion genuinely
 * gathered real {@link BlockCoverage} (proven live against WTA - the
 * minion log showed real test execution and real production-code log
 * output), but every block silently failed to resolve to a line here,
 * because {@code ClassPathByteArraySource} was looking for the target
 * module's `.class` files on a classpath that never had them - {@link
 * BlockLineResolver} then dropped every block with no error and no
 * warning (its own {@code resolvedLines == null || resolvedLines.isEmpty()}
 * early return, working exactly as designed against an input that was
 * already wrong). {@link PerTestDriver} now passes the real classpath file
 * path through {@link #CLASSPATH_FILE_PROPERTY} - the same channel {@link
 * #MODULE_ID_PROPERTY} already used, since a system property is the only
 * way to reach an SPI-instantiated instance like this one.
 */
public final class CoverdictLineExporter implements CoverageExporterFactory {

    static final String OUTPUT_FILE_NAME = "coverdict-line-tests.json";

    /** Set by {@link PerTestDriver} before calling {@code EntryPoint.execute} - the only channel available to an SPI-instantiated exporter. */
    static final String MODULE_ID_PROPERTY = "coverdict.pertest.moduleId";

    /** Set by {@link PerTestDriver}: path to the classpath list file (one entry per line) - the real target-module classpath, distinct from this driver JVM's own launch {@code -cp} (D-68). */
    static final String CLASSPATH_FILE_PROPERTY = "coverdict.pertest.classpathFile";

    /**
     * Set by {@link PerTestDriver}: the same {@code reportDir} it gave
     * {@code ReportOptions} - needed here only to atomically rename the
     * temp export into place (D-74), same "system property is the only
     * channel" reasoning as the two above.
     */
    static final String REPORT_DIR_PROPERTY = "coverdict.pertest.reportDir";

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
            ClassByteArraySource source = new ClassPathByteArraySource(targetClassPath());
            LineMap lineMap = new LineMapper(source);
            String moduleId = System.getProperty(MODULE_ID_PROPERTY, "");
            BlockLineResolver.Result resolved = BlockLineResolver.resolve(moduleId, List.copyOf(coverage), lineMap);
            String tempFileName = OUTPUT_FILE_NAME + ".tmp";
            try (Writer w = outputStrategy.createWriterForFile(tempFileName)) {
                PerTestJsonWriter.write(w, resolved.evidence());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            renameIntoPlace(tempFileName);
        }

        /**
         * D-74: {@link PerTestRunner} polls for {@link #OUTPUT_FILE_NAME}'s
         * existence and kills this process the moment it appears - but PIT's
         * own {@code DirectoryResultOutputStrategy} opens the file via a
         * plain {@code new FileWriter(path)}, which creates the (empty) file
         * on disk before a single byte of the actual JSON is written.
         * Verified the hard way against real gson: a one-class run's write
         * is fast enough to always finish first, but an ~85-class "scan
         * whole module" write is not - the poll loop was seeing the file
         * the instant it was created, killing this process mid-write, and
         * {@link PerTestJsonReader} correctly rejected the truncated result
         * as unreadable. Writing to a {@code .tmp} name instead and only
         * now, after the writer above is fully closed, atomically moving it
         * onto the real name means the poll loop can only ever observe
         * {@link #OUTPUT_FILE_NAME} in one of two states: absent, or
         * completely written - never partial.
         */
        private static void renameIntoPlace(String tempFileName) {
            String reportDir = System.getProperty(REPORT_DIR_PROPERTY);
            if (reportDir == null) {
                // No PerTestDriver in the loop (e.g. a test instantiating
                // this SPI directly) - nothing to rename onto, and nothing
                // is polling this process to death either, so leaving the
                // temp file under its own name would just orphan it.
                return;
            }
            Path dir = Path.of(reportDir);
            try {
                Files.move(dir.resolve(tempFileName), dir.resolve(OUTPUT_FILE_NAME),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * The real target-module classpath (D-68), read from the file
         * {@link #CLASSPATH_FILE_PROPERTY} names. Falls back to PIT's
         * default no-arg {@link ClassPath} (this driver JVM's own {@code
         * -cp}) when the property is unset - a bare unit test instantiating
         * this SPI directly, outside {@link PerTestDriver}, is the only
         * case that should ever hit this path; never throws, since a
         * degraded classpath is still better than aborting evidence
         * collection entirely (hard rule 3a).
         */
        private static ClassPath targetClassPath() {
            String classpathFilePath = System.getProperty(CLASSPATH_FILE_PROPERTY);
            if (classpathFilePath == null) {
                return new ClassPath();
            }
            try {
                List<String> lines = Files.readAllLines(Path.of(classpathFilePath), StandardCharsets.UTF_8);
                List<File> files = new ArrayList<>(lines.size());
                for (String line : lines) {
                    if (!line.isBlank()) {
                        files.add(new File(line));
                    }
                }
                return new ClassPath(files);
            } catch (IOException e) {
                return new ClassPath();
            }
        }
    }
}
