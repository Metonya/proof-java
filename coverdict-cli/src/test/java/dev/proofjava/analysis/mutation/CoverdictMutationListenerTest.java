package dev.proofjava.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationStatusTestPair;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;

/**
 * Exercises {@link CoverdictMutationListener} through PIT's real listener
 * interfaces without spawning PIT itself - {@link ListenerArguments} and
 * {@link ClassMutationResults}/{@link MutationResult} all have public
 * constructors (verified via javap), so the SPI contract is tested end to
 * end down to the wire JSON.
 */
class CoverdictMutationListenerTest {

    private static final String MODULE_ID_PROPERTY = "coverdict.mutation.moduleId";

    @AfterEach
    void clearModuleIdProperty() {
        System.clearProperty(MODULE_ID_PROPERTY);
    }

    @Test
    void writesAccumulatedResultsToTheOutputStrategyOnRunEnd() throws IOException {
        System.setProperty(MODULE_ID_PROPERTY, "demo-module");
        StringWriter captured = new StringWriter();
        CoverdictMutationListener factory = new CoverdictMutationListener();
        MutationResultListener listener = factory.getListener(new Properties(),
            listenerArguments(name -> {
                assertEquals("coverdict-mutants.json", name);
                return captured;
            }));

        listener.runStart();
        listener.handleMutationResult(new ClassMutationResults(List.of(
            mutationResult("com/example/Calc", "add", "(II)I", 10, DetectionStatus.SURVIVED))));
        listener.runEnd();

        MutationModuleEvidence written = MutationJsonReader.read(
            new ByteArrayInputStream(captured.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("demo-module", written.moduleId());
        assertEquals(1, written.methods().size());
        assertTrue(written.warnings().isEmpty());
    }

    @Test
    void nameMatchesTheConstantUsedForOutputFormatActivation() {
        assertEquals("coverdict-mutation", new CoverdictMutationListener().name());
    }

    private static MutationResult mutationResult(String internalClassName, String methodName, String desc,
                                                   int line, DetectionStatus status) {
        Location location = new Location(ClassName.fromString(internalClassName), methodName, desc);
        MutationIdentifier id = new MutationIdentifier(location, 0, "RETURNS");
        MutationDetails details = new MutationDetails(id, internalClassName + ".java", "a mutant", line, 0);
        return new MutationResult(details, new MutationStatusTestPair(0, status, List.of(), List.of()));
    }

    private static ListenerArguments listenerArguments(org.pitest.util.ResultOutputStrategy outputStrategy) {
        return new ListenerArguments(outputStrategy, null, null, null, 0L, true, null);
    }
}
