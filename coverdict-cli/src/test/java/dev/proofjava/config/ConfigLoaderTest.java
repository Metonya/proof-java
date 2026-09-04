package dev.proofjava.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    // --- modules (D-66): the config-file shape of --module/--report/--per-test-classpath/--mutation-classpath ---

    @Test
    void modulesRoundTripEveryField() throws IOException {
        writeConfig("""
            {
              "modules": [
                {"id": "app", "root": "app", "report": "app/target/site/jacoco/jacoco.xml"},
                {"id": "data", "root": "data", "sourceRoots": ["data/src/main/java"],
                 "testRoots": ["data/src/test/java"], "report": "data/target/site/jacoco/jacoco.xml",
                 "perTestClasspath": "data/target/coverdict-per-test-classpath.txt",
                 "mutationClasspath": "data/target/coverdict-mutation-classpath.txt"}
              ]
            }
            """);

        CoverdictConfig config = ConfigLoader.load(repoRoot, null);

        assertEquals(2, config.modules().size());
        CoverdictConfig.ModuleConfig app = config.modules().get(0);
        assertEquals("app", app.id());
        assertEquals("app", app.root());
        assertNull(app.sourceRoots(), "unset stays null, distinct from an authored empty list");
        assertEquals("app/target/site/jacoco/jacoco.xml", app.report());
        assertNull(app.perTestClasspath());

        CoverdictConfig.ModuleConfig data = config.modules().get(1);
        assertEquals(List.of("data/src/main/java"), data.sourceRoots());
        assertEquals("data/target/coverdict-per-test-classpath.txt", data.perTestClasspath());
        assertEquals("data/target/coverdict-mutation-classpath.txt", data.mutationClasspath());
    }

    @Test
    void aModuleWithNoReportIsAllowedInConfig() throws IOException {
        // Mirrors an unbound --module: the module is simply excluded from
        // the analyzed set (MODULE_WITHOUT_REPORT) rather than rejected here.
        writeConfig("{\"modules\": [{\"id\": \"app\", \"root\": \"app\"}]}");

        CoverdictConfig config = ConfigLoader.load(repoRoot, null);

        assertNull(config.modules().get(0).report());
    }

    @Test
    void aDuplicateModuleIdIsRejected() throws IOException {
        writeConfig("{\"modules\": [{\"id\": \"app\", \"root\": \"a\"}, {\"id\": \"app\", \"root\": \"b\"}]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("Duplicate module id 'app'"), e.getMessage());
    }

    @Test
    void aModuleMissingRootIsRejected() throws IOException {
        writeConfig("{\"modules\": [{\"id\": \"app\"}]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("'id' and 'root'"), e.getMessage());
    }

    @Test
    void anUnknownKeyInsideAModuleIsRejected() throws IOException {
        writeConfig("{\"modules\": [{\"id\": \"app\", \"root\": \"app\", \"typoRoot\": \"x\"}]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("typoRoot"), e.getMessage());
    }

    // --- strictness: each of these would otherwise be a silent misconfiguration ---

    @Test
    void anUnknownKeyInsideASuppressionIsRejected() throws IOException {
        writeConfig("{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\","
            + " \"reason\": \"r\", \"because\": \"typo\"}]}");

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains("because"), e.getMessage());
    }

    /**
     * Four independently-motivated rejections that share one shape (SonarQube
     * java:S5976): each names a distinct kind of invalid document, and each
     * assertion checks that the exception message actually names the specific
     * problem - not merely that some exception was thrown.
     */
    static Stream<Arguments> rejectedDocumentsWithAMeaningfulMessage() {
        return Stream.of(
            Arguments.of("an unknown top-level key (typo'd 'supressions', one 'p' - the case this rule exists for)",
                "{\"supressions\": []}", "supressions"),
            Arguments.of("a suppression missing its mandatory reason",
                "{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\"}]}", "reason"),
            Arguments.of("a duplicate top-level key (rather than silently letting the last one win)",
                "{\"languageLevel\": 11, \"languageLevel\": 17}", "Duplicate"),
            Arguments.of("a customOracles entry with no '#' separating type and method",
                "{\"customOracles\": [\"NoHashHere\"]}", "NoHashHere"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedDocumentsWithAMeaningfulMessage")
    void rejectsWithAMessageNamingTheProblem(String scenario, String json, String expectedInMessage)
            throws IOException {
        writeConfig(json);

        ConfigException e = assertThrows(ConfigException.class, () -> ConfigLoader.load(repoRoot, null));

        assertTrue(e.getMessage().contains(expectedInMessage), e.getMessage());
    }

    /** Three more rejections (java:S5976) where only "it throws" matters, not the message's exact wording. */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"languageLevel\": \"17\"}",          // wrong value type
        "{\"languageLevel\": 17,,}",            // malformed JSON
        "{\"findingsScope\": \"everything\"}"   // unknown enum value
    })
    void rejectsAnInvalidDocument(String json) throws IOException {
        writeConfig(json);

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
            "{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\", \"reason\": \"r\"}]}",
            "{\"modules\": [{\"id\": \"app\", \"root\": \"app\", \"report\": \"app/jacoco.xml\"}]}");
        List<String> invalid = List.of(
            "{\"supressions\": []}",
            "{\"languageLevel\": \"17\"}",
            "{\"findingsScope\": \"everything\"}",
            "{\"customOracles\": [\"NoHashHere\"]}",
            "{\"suppressions\": [{\"rule\": \"NULL_CHECK_ONLY\", \"pathGlob\": \"**\"}]}",
            "{\"suppressions\": [{\"rule\": \"NOT_A_RULE\", \"pathGlob\": \"**\", \"reason\": \"r\"}]}",
            "{\"modules\": [{\"id\": \"app\"}]}",
            "{\"modules\": [{\"id\": \"app\", \"root\": \"app\", \"typoRoot\": \"x\"}]}");

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
