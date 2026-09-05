package dev.proofjava.analysis.mutation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;
import org.pitest.util.ResultOutputStrategy;

import dev.proofjava.analysis.subprocess.ProgressMarker;

/**
 * PIT {@link MutationResultListenerFactory} SPI implementation (D-56):
 * accumulates every {@link ClassMutationResults} PIT hands it and writes
 * {@link MutationJsonWriter}'s wire format to {@code proof-mutants.json} in the
 * run's report directory. The file is rewritten as classes complete, not only
 * at the end (D-85), so a run killed at its budget still leaves behind the
 * classes it did measure. Registered via {@code META-INF/services/
 * org.pitest.mutationtest.MutationResultListenerFactory}.
 *
 * <p>Activation is two-gated, unlike {@code ProofLineExporter}'s single
 * {@code Feature} gate: {@link org.pitest.mutationtest.config.SettingsFactory#createListener()}
 * only instantiates a listener whose {@link #name()} appears in {@code
 * ReportOptions.getOutputFormats()} (confirmed via {@code javap} - {@code
 * SettingsFactory.findListeners()} filters the SPI-discovered set by output
 * format name before {@code provides()} is even consulted). {@link
 * #provides()} is deliberately not overridden, so it keeps the interface's
 * default {@code LEGACY_MODE} feature ({@code onByDefault=true}) -
 * activation is entirely through {@link #NAME} being requested, not a
 * feature toggle.
 */
public final class ProofMutationListener implements MutationResultListenerFactory {

    static final String NAME = "proof-mutation";
    static final String OUTPUT_FILE_NAME = "proof-mutants.json";

    /** Set by {@code MutationDriver} before calling {@code EntryPoint.execute} - the only channel available to an SPI-instantiated listener. */
    static final String MODULE_ID_PROPERTY = "proof.mutation.moduleId";

    /** Set by {@code MutationDriver}, like {@link #MODULE_ID_PROPERTY} - the directory the incremental flush renames onto. */
    static final String REPORT_DIR_PROPERTY = "proof.mutation.reportDir";

    /**
     * Minimum gap between incremental flushes. Rewriting the whole document per
     * class is O(n^2) in bytes, which is free for a diff-scoped run and wasteful
     * for a several-hundred-class one; throttling bounds the I/O while keeping
     * the window of work that a kill can destroy down to seconds.
     */
    private static final long FLUSH_INTERVAL_MILLIS = 2_000L;

    @Override
    public MutationResultListener getListener(Properties props, ListenerArguments args) {
        return new AccumulatingListener(args.getOutputStrategy());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "proof-java mutant kill-set exporter";
    }

    private static final class AccumulatingListener implements MutationResultListener {
        private final ResultOutputStrategy outputStrategy;
        private final List<ClassMutationResults> results = new ArrayList<>();
        private long lastFlushMillis;

        AccumulatingListener(ResultOutputStrategy outputStrategy) {
            this.outputStrategy = outputStrategy;
        }

        @Override
        public void runStart() {
            // nothing to initialize - results accumulates lazily
        }

        @Override
        public void handleMutationResult(ClassMutationResults classResults) {
            results.add(classResults);
            // D-64: PIT calls this once per mutated class, which makes it the
            // only stable per-class progress hook either driver has. The
            // parent turns these into a live counter; a module that never
            // emits one never reached the mutation phase at all.
            ProgressMarker.emit(results.size());

            long now = System.currentTimeMillis();
            if (now - lastFlushMillis >= FLUSH_INTERVAL_MILLIS) {
                lastFlushMillis = now;
                flush();
            }
        }

        @Override
        public void runEnd() {
            flush();
        }

        /**
         * D-85: writes to a {@code .tmp} name and moves it onto the real one, so
         * a reader - or a kill arriving mid-write - only ever observes the file
         * absent or complete, never truncated. Same guarantee, and the same
         * reason, as {@code ProofLineExporter}'s D-74 rename.
         *
         * <p>An {@link IOException} here is swallowed rather than thrown: this
         * runs inside PIT's own callback, and failing a flush must not abort a
         * mutation run that is otherwise producing real evidence. The next
         * flush, or {@link #runEnd()}, writes the accumulated results again.
         */
        private void flush() {
            String moduleId = System.getProperty(MODULE_ID_PROPERTY, "");
            MutationModuleEvidence evidence = MutationResultAccumulator.accumulate(moduleId, results);
            String reportDir = System.getProperty(REPORT_DIR_PROPERTY);
            if (reportDir == null) {
                // No MutationDriver in the loop (a test instantiating this SPI
                // directly): nothing to rename onto, so write in place.
                try (Writer w = outputStrategy.createWriterForFile(OUTPUT_FILE_NAME)) {
                    MutationJsonWriter.write(w, evidence);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return;
            }
            String tempFileName = OUTPUT_FILE_NAME + ".tmp";
            try {
                try (Writer w = outputStrategy.createWriterForFile(tempFileName)) {
                    MutationJsonWriter.write(w, evidence);
                }
                Path dir = Path.of(reportDir);
                Files.move(dir.resolve(tempFileName), dir.resolve(OUTPUT_FILE_NAME),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Deliberately not fatal - see the javadoc above.
            }
        }
    }
}
