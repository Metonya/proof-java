package dev.coverdict.analysis.pertest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link PerTestJsonReader} is only ever exercised in production inside
 * {@link PerTestRunner}'s subprocess, so it had zero direct test coverage
 * despite being pure JSON parsing with no process to spawn - mirrors
 * {@code MutationJsonRoundTripTest}.
 */
class PerTestJsonRoundTripTest {

    @Test
    void writerAndReaderRoundTripAFullEvidenceRecord() throws IOException {
        PerTestLine line1 = new PerTestLine(40, List.of("com.example.CalcTest#rejectsNegative()"));
        PerTestLine line2 = new PerTestLine(41, List.of(
            "com.example.CalcTest#acceptsPositive()", "com.example.CalcTest#rejectsNegative()"));
        PerTestEntry entry = new PerTestEntry("com.example.Calc", "add", List.of(line1, line2));
        PerTestEntry ambient = new PerTestEntry("com.example.Calc", "<clinit>",
            List.of(new PerTestLine(12, List.of("com.example.CalcTest#acceptsPositive()"))));
        PerTestModuleEvidence original = new PerTestModuleEvidence("m", List.of(entry), List.of(ambient));

        assertEquals(original, roundTrip(original));
    }

    @Test
    void roundTripsEmptyEntriesAndAmbient() throws IOException {
        PerTestModuleEvidence original = new PerTestModuleEvidence("m", List.of(), List.of());

        assertEquals(original, roundTrip(original));
    }

    @Test
    void roundTripsMultipleTestsOnOneLine() throws IOException {
        PerTestLine line = new PerTestLine(5, List.of("a.Test#one()", "a.Test#two()", "a.Test#three()"));
        PerTestEntry entry = new PerTestEntry("a.Subject", "m", List.of(line));
        PerTestModuleEvidence original = new PerTestModuleEvidence("m", List.of(entry), List.of());

        assertEquals(original, roundTrip(original));
    }

    private static PerTestModuleEvidence roundTrip(PerTestModuleEvidence evidence) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (var writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
            PerTestJsonWriter.write(writer, evidence);
        }
        try (var in = new ByteArrayInputStream(buffer.toByteArray())) {
            return PerTestJsonReader.read(in);
        }
    }
}
