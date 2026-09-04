package dev.proofjava.analysis.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.proofjava.analysis.model.AnalysisReason;

class MutationJsonRoundTripTest {

    @Test
    void writerAndReaderRoundTripAFullEvidenceRecord() throws IOException {
        Mutant killed = new Mutant("RETURNS", 10, "KILLED", List.of("com.example.ATest#m"));
        Mutant survived = new Mutant("VOID_METHOD_CALLS", 12, "SURVIVED", List.of());
        MutatedMethod method = new MutatedMethod("com.example.Calc", "add", "(II)I", 10, 12, List.of(killed, survived));
        AnalysisReason warning = new AnalysisReason("MUTATION_TRUNCATED", "too many", null, "m", 5);
        MutationModuleEvidence original = new MutationModuleEvidence("m", List.of(method), List.of(warning));

        MutationModuleEvidence roundTripped = roundTrip(original);

        assertEquals(original, roundTripped);
    }

    @Test
    void roundTripsEmptyMethodsAndWarnings() throws IOException {
        MutationModuleEvidence original = new MutationModuleEvidence("m", List.of(), List.of());

        assertEquals(original, roundTrip(original));
    }

    @Test
    void roundTripsAWarningWithNullOptionalFields() throws IOException {
        AnalysisReason warning = new AnalysisReason("SOME_CODE", "message only");
        MutationModuleEvidence original = new MutationModuleEvidence("m", List.of(), List.of(warning));

        assertEquals(original, roundTrip(original));
    }

    private static MutationModuleEvidence roundTrip(MutationModuleEvidence evidence) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (var writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
            MutationJsonWriter.write(writer, evidence);
        }
        try (var in = new ByteArrayInputStream(buffer.toByteArray())) {
            return MutationJsonReader.read(in);
        }
    }
}
