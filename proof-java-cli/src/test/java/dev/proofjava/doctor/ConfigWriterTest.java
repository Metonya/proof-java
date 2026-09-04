package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;

/** {@code doctor --write-config}'s output must be exactly what {@code ConfigLoader}/the schema accept - proven here against the real schema, not a hand-rolled assumption. */
class ConfigWriterTest {

    @TempDir
    Path repoRoot;

    private static final Path SCHEMA_FILE = Path.of("../schema/proof-config.schema.json");
    private static final MavenModule APP = new MavenModule("app", "app");
    private static final MavenModule DATA = new MavenModule("data", "data");

    @Test
    void writesOnlyUsableModules() throws IOException {
        ModuleDiagnosis usable = new ModuleDiagnosis(APP, List.of(DoctorCheck.ok("JACOCO_REPORT_PRESENT", "found")),
            "app/target/site/jacoco/jacoco.xml", null, null);
        ModuleDiagnosis blocked = new ModuleDiagnosis(DATA, List.of(DoctorCheck.blocker("JACOCO_REPORT_STALE", "stale")),
            "data/target/site/jacoco/jacoco.xml", null, null);
        Path target = repoRoot.resolve("proof.config.json");

        boolean wrote = ConfigWriter.write(List.of(usable, blocked), target);

        assertTrue(wrote);
        String content = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"app\""));
        assertFalse(content.contains("\"data\""), "a module with a BLOCKER must not be written");
    }

    @Test
    void writesNothingWhenNoModuleIsUsable() {
        ModuleDiagnosis blocked = new ModuleDiagnosis(APP, List.of(DoctorCheck.blocker("JACOCO_REPORT_MISSING", "missing")),
            null, null, null);
        Path target = repoRoot.resolve("proof.config.json");

        boolean wrote = ConfigWriter.write(List.of(blocked), target);

        assertFalse(wrote);
        assertFalse(Files.exists(target));
    }

    @Test
    void includesClasspathFieldsOnlyWhenPresent() throws IOException {
        ModuleDiagnosis withClasspaths = new ModuleDiagnosis(DATA, List.of(DoctorCheck.ok("JACOCO_REPORT_PRESENT", "found")),
            "data/target/site/jacoco/jacoco.xml", "data/target/proof-per-test-classpath.txt",
            "data/target/proof-mutation-classpath.txt");
        Path target = repoRoot.resolve("proof.config.json");

        ConfigWriter.write(List.of(withClasspaths), target);

        String content = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(content.contains("perTestClasspath"));
        assertTrue(content.contains("mutationClasspath"));
    }

    /** The real, most important guarantee: what this class writes must actually be readable and schema-valid. */
    @Test
    void theWrittenFileIsValidAgainstTheRealSchemaAndLoadsBackCleanly() throws IOException {
        ModuleDiagnosis app = new ModuleDiagnosis(APP, List.of(DoctorCheck.ok("JACOCO_REPORT_PRESENT", "found")),
            "app/target/site/jacoco/jacoco.xml", "app/target/proof-per-test-classpath.txt", null);
        Path target = repoRoot.resolve("proof.config.json");

        ConfigWriter.write(List.of(app), target);

        JsonSchema schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(Files.readString(SCHEMA_FILE));
        JsonNode node = new ObjectMapper().readTree(Files.readAllBytes(target));
        assertEquals(java.util.Set.of(), schema.validate(node), schema.validate(node).toString());

        var config = dev.proofjava.config.ConfigLoader.load(repoRoot, null);
        assertEquals(1, config.modules().size());
        assertEquals("app", config.modules().get(0).id());
        assertEquals("app/target/proof-per-test-classpath.txt", config.modules().get(0).perTestClasspath());
    }

    /** Module ids/paths could in principle carry a quote or backslash - not attacker input here, but real JSON escaping is cheap and the alternative is a config file the writer's own reader cannot parse back. */
    @Test
    void escapesSpecialCharactersInWrittenStrings() {
        MavenModule odd = new MavenModule("app\"quoted", "app");
        ModuleDiagnosis d = new ModuleDiagnosis(odd, List.of(DoctorCheck.ok("JACOCO_REPORT_PRESENT", "found")),
            "app/jacoco.xml", null, null);
        Path target = repoRoot.resolve("proof.config.json");

        ConfigWriter.write(List.of(d), target);

        var config = dev.proofjava.config.ConfigLoader.load(repoRoot, null);
        assertEquals("app\"quoted", config.modules().get(0).id());
    }
}
