package dev.coverdict.analysis.pertest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Reads back what {@link PerTestJsonWriter} wrote, via Jackson's streaming
 * {@link JsonParser} - the parent process's half of the exporter-to-driver
 * wire format. Not schema-validated (it is coverdict's own output, from the
 * same version of this code, never third-party input); malformed content
 * throws, converted by {@link PerTestRunner} into a warning like any other
 * per-test collection failure.
 */
public final class PerTestJsonReader {

    private static final JsonFactory FACTORY = new JsonFactory();

    private PerTestJsonReader() {
    }

    public static PerTestModuleEvidence read(InputStream in) throws IOException {
        try (JsonParser p = FACTORY.createParser(in)) {
            String moduleId = null;
            List<PerTestEntry> entries = List.of();
            List<PerTestEntry> ambient = List.of();
            expect(p, JsonToken.START_OBJECT);
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String field = p.currentName();
                p.nextToken();
                switch (field) {
                    case "moduleId" -> moduleId = p.getValueAsString();
                    case "entries" -> entries = readEntries(p);
                    case "ambient" -> ambient = readEntries(p);
                    default -> p.skipChildren();
                }
            }
            return new PerTestModuleEvidence(moduleId, entries, ambient);
        }
    }

    private static List<PerTestEntry> readEntries(JsonParser p) throws IOException {
        expect(p, JsonToken.START_ARRAY);
        List<PerTestEntry> entries = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            entries.add(readEntry(p));
        }
        return entries;
    }

    private static PerTestEntry readEntry(JsonParser p) throws IOException {
        String className = null;
        String methodName = null;
        List<PerTestLine> lines = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "className" -> className = p.getValueAsString();
                case "methodName" -> methodName = p.getValueAsString();
                case "lines" -> lines = readLines(p);
                default -> p.skipChildren();
            }
        }
        return new PerTestEntry(className, methodName, lines);
    }

    private static List<PerTestLine> readLines(JsonParser p) throws IOException {
        expect(p, JsonToken.START_ARRAY);
        List<PerTestLine> lines = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            int line = -1;
            List<String> tests = List.of();
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String field = p.currentName();
                p.nextToken();
                if ("line".equals(field)) {
                    line = p.getValueAsInt();
                } else if ("tests".equals(field)) {
                    tests = readStrings(p);
                } else {
                    p.skipChildren();
                }
            }
            lines.add(new PerTestLine(line, tests));
        }
        return lines;
    }

    private static List<String> readStrings(JsonParser p) throws IOException {
        expect(p, JsonToken.START_ARRAY);
        List<String> values = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            values.add(p.getValueAsString());
        }
        return values;
    }

    private static void expect(JsonParser p, JsonToken token) throws IOException {
        if (p.currentToken() != token && p.nextToken() != token) {
            throw new IOException("Expected " + token + " but found " + p.currentToken());
        }
    }
}
