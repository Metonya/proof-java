package dev.coverdict.analysis.mutation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;
import org.pitest.util.ResultOutputStrategy;

/**
 * PIT {@link MutationResultListenerFactory} SPI implementation (D-56):
 * accumulates every {@link ClassMutationResults} PIT hands it in RAM and
 * writes {@link MutationJsonWriter}'s wire format to {@code
 * coverdict-mutants.json} in the run's report directory once the mutation
 * phase finishes. Registered via {@code META-INF/services/
 * org.pitest.mutationtest.MutationResultListenerFactory}.
 *
 * <p>Activation is two-gated, unlike {@code CoverdictLineExporter}'s single
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
public final class CoverdictMutationListener implements MutationResultListenerFactory {

    static final String NAME = "coverdict-mutation";
    static final String OUTPUT_FILE_NAME = "coverdict-mutants.json";

    /** Set by {@code MutationDriver} before calling {@code EntryPoint.execute} - the only channel available to an SPI-instantiated listener. */
    static final String MODULE_ID_PROPERTY = "coverdict.mutation.moduleId";

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
        return "coverdict mutant kill-set exporter";
    }

    private static final class AccumulatingListener implements MutationResultListener {
        private final ResultOutputStrategy outputStrategy;
        private final List<ClassMutationResults> results = new ArrayList<>();

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
        }

        @Override
        public void runEnd() {
            String moduleId = System.getProperty(MODULE_ID_PROPERTY, "");
            MutationModuleEvidence evidence = MutationResultAccumulator.accumulate(moduleId, results);
            try (Writer w = outputStrategy.createWriterForFile(OUTPUT_FILE_NAME)) {
                MutationJsonWriter.write(w, evidence);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
