package dev.coverdict.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

import picocli.CommandLine;

/** Runs the real fixtures end to end through the picocli-wired command, the way a user actually invokes it. */
class AnalyzeCommandTest {

    // Absolute: --report paths are resolved against --repo (a @TempDir here,
    // unrelated to this module's own basedir), so a relative fixture path
    // would be looked up in the wrong place.
    private static final Path FIXTURES = Path.of("../fixtures/jacoco").toAbsolutePath();
    private static final Path SCHEMA_FILE = Path.of("../schema/coverdict-verdict.schema.json");

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @TempDir
    Path repoRoot;

    private int run(String... args) {
        CommandLine cmd = Main.commandLine();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute(args);
    }

    private Set<ValidationMessage> validate(Path jsonFile) throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V202012);
        JsonSchema schema;
        try (var in = Files.newInputStream(SCHEMA_FILE)) {
            schema = factory.getSchema(in);
        }
        JsonNode node = new ObjectMapper().readTree(Files.readAllBytes(jsonFile));
        return schema.validate(node);
    }

    @Test
    void singleModuleShorthandProducesASchemaValidCompleteVerdict() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(Files.exists(outFile));
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("complete", doc.at("/analysis/status").asText());
        assertEquals(80.0, doc.at("/coverage/overall/jacoco-line/percent").asDouble());
        assertEquals("unavailable_no_vcs", doc.at("/coverage/newCode/status").asText());
        assertEquals("root", doc.at("/inputs/modules/0/id").asText());
    }

    @Test
    void malformedReportProducesASchemaValidIncompleteVerdictAndExitThree() throws IOException {
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--report", FIXTURES.resolve("malformed.xml").toString(),
            "--out", outFile.toString());

        assertEquals(ExitCode.INCOMPLETE.value(), exitCode);
        assertTrue(Files.exists(outFile), "hard rule 3a: an incomplete analysis still writes a structured document");
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals("incomplete", doc.at("/analysis/status").asText());
        assertEquals("MALFORMED_JACOCO_XML", doc.at("/analysis/incompleteReasons/0/code").asText());
    }

    @Test
    void bareReportCombinedWithModuleIsInvalidInvocationAndWritesNoJson() {
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "demo=.",
            "--report", FIXTURES.resolve("mixed-coverage.xml").toString(), // bare, no id=
            "--out", outFile.toString());

        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
        assertFalse(Files.exists(outFile), "exit 2 must never write a verdict document (schema note)");
    }

    @Test
    void missingNoVcsFlagIsInvalidInvocation() {
        int exitCode = run("analyze", "--report", FIXTURES.resolve("mixed-coverage.xml").toString());
        assertEquals(ExitCode.INVALID_INPUT.value(), exitCode);
    }

    @Test
    void moduleWithoutAnyReportIsExcludedWithAWarningNotAnError() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));
        Files.writeString(repoRoot.resolve("src/main/java/com/example/Calc.java"), "class Calc {}\n");
        Path outFile = repoRoot.resolve("verdict.json");

        int exitCode = run("analyze", "--no-vcs",
            "--repo", repoRoot.toString(),
            "--module", "used=.",
            "--module", "unused=.",
            "--report", "used=" + FIXTURES.resolve("mixed-coverage.xml"),
            "--out", outFile.toString());

        assertEquals(ExitCode.COMPLETE.value(), exitCode);
        assertTrue(validate(outFile).isEmpty(), validate(outFile).toString());

        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(outFile));
        assertEquals(1, doc.at("/inputs/modules").size(), "module without a report must not appear as evidence");
        boolean warned = false;
        for (JsonNode w : doc.at("/warnings")) {
            if (w.at("/code").asText().equals("MODULE_WITHOUT_REPORT")) {
                warned = true;
            }
        }
        assertTrue(warned, doc.toString());
    }
}
