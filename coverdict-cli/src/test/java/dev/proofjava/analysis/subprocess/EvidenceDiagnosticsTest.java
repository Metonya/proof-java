package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D-64: the two diagnostic channels, and the rule that neither may ever fail a run. */
class EvidenceDiagnosticsTest {

    @TempDir
    Path tempDir;

    @Test
    void noneReportsNothingAndOpensNoLog() {
        EvidenceDiagnostics diagnostics = EvidenceDiagnostics.none();

        assertFalse(diagnostics.verbose());
        assertNull(diagnostics.openLog("app", "mutation"));
        diagnostics.progress("ignored"); // must not throw
    }

    @Test
    void progressOnlyReportsButStaysQuiet() {
        List<String> reported = new ArrayList<>();
        EvidenceDiagnostics diagnostics = EvidenceDiagnostics.progressOnly(reported::add);

        diagnostics.progress("mutation: module 'app' - 3 target class(es)");

        assertEquals(List.of("mutation: module 'app' - 3 target class(es)"), reported);
        assertFalse(diagnostics.verbose(), "verbosity is tied to having somewhere to put the output");
        assertNull(diagnostics.openLog("app", "mutation"));
    }

    @Test
    void aLogDirectoryTurnsTheRunVerboseAndNamesTheFilePerModuleAndLayer() throws IOException {
        EvidenceDiagnostics diagnostics = new EvidenceDiagnostics(tempDir.resolve("logs"), message -> { });

        assertTrue(diagnostics.verbose());
        try (Writer w = diagnostics.openLog("service", "mutation")) {
            assertNotNull(w);
            w.write("hello");
        }

        assertEquals("hello", Files.readString(tempDir.resolve("logs").resolve("service-mutation.log")));
    }

    /** Module ids reach this from the command line; the same sanitizing rule {@link SubprocessWorkspace} uses applies. */
    @Test
    void sanitizesAModuleIdBeforeUsingItAsAFileName() throws IOException {
        EvidenceDiagnostics diagnostics = new EvidenceDiagnostics(tempDir, message -> { });

        try (Writer w = diagnostics.openLog("weird/id:1", "pertest")) {
            w.write("x");
        }

        assertTrue(Files.exists(tempDir.resolve("weird_id_1-pertest.log")));
    }

    /**
     * Hard rule 3a is about never reporting missing evidence as success -
     * it does not make a logging problem fatal. An unopenable log degrades
     * to a progress note and the run carries on collecting real evidence.
     */
    @Test
    void anUnopenableLogDegradesToAProgressNoteInsteadOfFailing() throws IOException {
        Path fileWhereADirectoryShouldBe = tempDir.resolve("not-a-dir");
        Files.writeString(fileWhereADirectoryShouldBe, "occupied");
        List<String> reported = new ArrayList<>();

        EvidenceDiagnostics diagnostics = new EvidenceDiagnostics(fileWhereADirectoryShouldBe, reported::add);

        assertNull(diagnostics.openLog("app", "mutation"));
        assertEquals(1, reported.size());
        assertTrue(reported.get(0).contains("continuing without it"), reported.get(0));
    }
}
