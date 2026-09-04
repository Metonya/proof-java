package dev.proofjava.analysis.mutation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import dev.proofjava.analysis.model.AnalysisReason;

/**
 * Reads back what {@link MutationJsonWriter} wrote, via Jackson's streaming
 * {@link JsonParser} - the parent process's half of the listener-to-collector
 * wire format. Not schema-validated (it is proof-java's own output, from the
 * same version of this code, never third-party input); malformed content
 * throws, converted by {@link MutationRunner} into a warning like any other
 * mutation collection failure.
 */
public final class MutationJsonReader {

    private static final JsonFactory FACTORY = new JsonFactory();

    private MutationJsonReader() {
    }

    public static MutationModuleEvidence read(InputStream in) throws IOException {
        try (JsonParser p = FACTORY.createParser(in)) {
            String moduleId = null;
            List<MutatedMethod> methods = List.of();
            List<AnalysisReason> warnings = List.of();
            expect(p, JsonToken.START_OBJECT);
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String field = p.currentName();
                p.nextToken();
                switch (field) {
                    case "moduleId" -> moduleId = p.getValueAsString();
                    case "methods" -> methods = readMethods(p);
                    case "warnings" -> warnings = readWarnings(p);
                    default -> p.skipChildren();
                }
            }
            return new MutationModuleEvidence(moduleId, methods, warnings);
        }
    }

    private static List<MutatedMethod> readMethods(JsonParser p) throws IOException {
        expect(p, JsonToken.START_ARRAY);
        List<MutatedMethod> methods = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            methods.add(readMethod(p));
        }
        return methods;
    }

    private static MutatedMethod readMethod(JsonParser p) throws IOException {
        String className = null;
        String methodName = null;
        String methodDescription = null;
        int firstLine = -1;
        int lastLine = -1;
        List<Mutant> mutants = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "className" -> className = p.getValueAsString();
                case "methodName" -> methodName = p.getValueAsString();
                case "methodDescription" -> methodDescription = p.getValueAsString();
                case "firstLine" -> firstLine = p.getValueAsInt();
                case "lastLine" -> lastLine = p.getValueAsInt();
                case "mutants" -> mutants = readMutants(p);
                default -> p.skipChildren();
            }
        }
        return new MutatedMethod(className, methodName, methodDescription, firstLine, lastLine, mutants);
    }

    private static List<Mutant> readMutants(JsonParser p) throws IOException {
        expect(p, JsonToken.START_ARRAY);
        List<Mutant> mutants = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            mutants.add(readMutant(p));
        }
        return mutants;
    }

    private static Mutant readMutant(JsonParser p) throws IOException {
        String mutator = null;
        int line = -1;
        String status = null;
        List<String> killingTests = List.of();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "mutator" -> mutator = p.getValueAsString();
                case "line" -> line = p.getValueAsInt();
                case "status" -> status = p.getValueAsString();
                case "killingTests" -> killingTests = readStrings(p);
                default -> p.skipChildren();
            }
        }
        return new Mutant(mutator, line, status, killingTests);
    }

    private static List<AnalysisReason> readWarnings(JsonParser p) throws IOException {
        expect(p, JsonToken.START_ARRAY);
        List<AnalysisReason> warnings = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            warnings.add(readWarning(p));
        }
        return warnings;
    }

    private static AnalysisReason readWarning(JsonParser p) throws IOException {
        String code = null;
        String message = null;
        String path = null;
        String module = null;
        Integer count = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "code" -> code = p.getValueAsString();
                case "message" -> message = p.getValueAsString();
                case "path" -> path = p.currentToken() == JsonToken.VALUE_NULL ? null : p.getValueAsString();
                case "module" -> module = p.currentToken() == JsonToken.VALUE_NULL ? null : p.getValueAsString();
                case "count" -> count = p.currentToken() == JsonToken.VALUE_NULL ? null : p.getValueAsInt();
                default -> p.skipChildren();
            }
        }
        return new AnalysisReason(code, message, path, module, count);
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
