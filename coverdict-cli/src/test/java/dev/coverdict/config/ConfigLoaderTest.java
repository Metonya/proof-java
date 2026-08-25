package dev.coverdict.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;

class ConfigLoaderTest {

    private static final Path SCHEMA_FILE = Path.of("../schema/coverdict-config.schema.json");

    @TempDir
    Path repoRoot;

    private Path writeConfig(String json) throws IOException {
        Path file = repoRoot.resolve(ConfigLoader.DEFAULT_FILE_NAME);
        Files.writeString(file, json);
        return file;
    }

    @Test
    void noConfigFileAnywhereIsAnEmptyConfigNotAnError() {
        CoverdictConfig config = ConfigLoader.load(repoRoot, null);

        assertNull(config.languageLevel());
        assertNull(config.coverageExclusions(), "unset must stay distinguishable from an explicitly empty list");
        assertTrue(config.customOracles().isEmpty());
        assertTrue(config.suppressions().isEmpty());
    }

    @Test
    void theDefaultFileAtTheRepoRootIsPickedUpWithoutBeingNamed() throws IOException {
        writeConfig("{\"languageLevel\": 11, \"encoding\": \"ISO-8859-9\"}");

        CoverdictConfig config = ConfigLoader.load(repoRoot, null);

        assertEquals(11, config.languageLevel());
        assertEquals("ISO-8859-9", config.encoding());
    }

    @Test
    void anExplicitlyNamedConfigThatDoesNotExistIsAnError() {
        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, "nope.json"));

        assertTrue(e.getMessage().contains("nope.json"), e.getMessage());
    }

    @Test
    void everyFieldRoundTripsIncludingAnExplicitlyEmptyExclusionList() throws IOException {
        writeConfig("""
            {
              "languageLevel": 17,
              "encoding": "UTF-8",
              "coverageExclusions": [],
              "findingsScope": "changed",
              "customOracles": ["com.example.MoreAsserts#assert*"],
              "suppressions": [
                {"rule": "NO_RECOGNIZED_ORACLE", "pathGlob": "**/Generated*.java", "reason": "generated code"}
              ]
            }
            """);

        CoverdictConfig config = ConfigLoader.load(repoRoot, null);

        assertEquals(17, config.languageLevel());
        assertEquals(List.of(), config.coverageExclusions(), "an authored empty list is not 'unset'");
        assertEquals("changed", config.findingsScope());
        assertEquals(List.of("com.example.MoreAsserts#assert*"), config.customOracles());
        assertEquals(1, config.suppressions().size());
        assertEquals("generated code", config.suppressions().get(0).reason());
        assertNull(config.suppressions().get(0).testMethodPattern(), "optional field stays null when absent");
    }

    // --- strictness: each of these would otherwise be a silent misconfiguration ---

    @Test
    void anUnknownTopLevelKeyIsRejectedRatherThanIgnored() throws IOException {
        writeConfig("{\"supressions\": []}"); // one 'p' - the typo this rule exists for

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("supressions"), e.getMessage());
    }

    @Test
    void anUnknownKeyInsideASuppressionIsRejected() throws IOException {
        writeConfig("{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\","
            + " \"reason\": \"r\", \"because\": \"typo\"}]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("because"), e.getMessage());
    }

    @Test
    void aSuppressionWithoutAReasonIsRejected() throws IOException {
        writeConfig("{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\"}]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("reason"), e.getMessage());
    }

    @Test
    void aWrongValueTypeIsRejected() throws IOException {
        writeConfig("{\"languageLevel\": \"17\"}");

        assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));
    }

    @Test
    void malformedJsonIsRejected() throws IOException {
        writeConfig("{\"languageLevel\": 17,,}");

        assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));
    }

    @Test
    void aDuplicateKeyIsRejectedRatherThanLastOneWinning() throws IOException {
        writeConfig("{\"languageLevel\": 11, \"languageLevel\": 17}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("Duplicate"), e.getMessage());
    }

    @Test
    void aCustomOracleEntryWithoutATypeAndMethodIsRejected() throws IOException {
        writeConfig("{\"customOracles\": [\"NoHashHere\"]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("NoHashHere"), e.getMessage());
    }

    @Test
    void anUnknownFindingsScopeValueIsRejected() throws IOException {
        writeConfig("{\"findingsScope\": \"everything\"}");

        assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));
    }

    /**
     * The reader is hand-written (D-40) precisely so the shipped jar needs no
     * schema-validator dependency - which makes it possible for the reader and
     * the checked-in schema to drift apart. This is the guard: every document
     * above is run through the real JSON Schema too, and the two must agree on
     * accept/reject. A schema edit that the parser does not implement (or the
     * reverse) fails here.
     */
    @Test
    void theCheckedInSchemaAndTheHandWrittenReaderAgree() throws IOException {
        JsonSchema schema = JsonSchemaFactory.getInstance(VersionFlag.V202012)
            .getSchema(Files.readString(SCHEMA_FILE));
        ObjectMapper mapper = new ObjectMapper();

        List<String> valid = List.of(
            "{}",
            "{\"languageLevel\": 17}",
            "{\"coverageExclusions\": []}",
            "{\"findingsScope\": \"changed\"}",
            "{\"customOracles\": [\"com.example.MoreAsserts#assert*\"]}",
            "{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\", \"reason\": \"r\"}]}");
        List<String> invalid = List.of(
            "{\"supressions\": []}",
            "{\"languageLevel\": \"17\"}",
            "{\"findingsScope\": \"everything\"}",
            "{\"customOracles\": [\"NoHashHere\"]}",
            "{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\"}]}",
            "{\"suppressions\": [{\"rule\": \"NOT_A_RULE\", \"pathGlob\": \"**\", \"reason\": \"r\"}]}");

        for (String json : valid) {
            assertTrue(schema.validate(mapper.readTree(json)).isEmpty(), "schema should accept: " + json);
            writeConfig(json);
            ConfigLoader.load(repoRoot, null); // must not throw
        }
        for (String json : invalid) {
            assertTrue(!schema.validate(mapper.readTree(json)).isEmpty(), "schema should reject: " + json);
            writeConfig(json);
            assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null), "reader should reject: " + json);
        }
    }
}
