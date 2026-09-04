package dev.proofjava.analysis.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofjava.analysis.model.ModuleDefinition;

class TestSourceScannerTest {

    @TempDir
    Path repoRoot;

    @Test
    void findsJavaFilesUnderADeclaredTestRoot() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Files.writeString(repoRoot.resolve("src/test/java/com/example/CalcTest.java"), "class CalcTest {}\n");
        ModuleDefinition module = new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));

        List<TestSourceFile> found = TestSourceScanner.scan(repoRoot, List.of(module), null);

        assertEquals(1, found.size());
        assertEquals("src/test/java/com/example/CalcTest.java", found.get(0).repoRelativePath());
    }

    @Test
    void aMissingTestRootContributesNoFilesAndIsNotAnError() {
        ModuleDefinition module = new ModuleDefinition("demo", ".", List.of(), List.of("does/not/exist"));
        List<TestSourceFile> found = TestSourceScanner.scan(repoRoot, List.of(module), null);
        assertEquals(List.of(), found);
    }

    @Test
    void aSymlinkedTestFileEscapingTheRepoRootIsNotScanned() throws IOException {
        // Windows requires an elevated privilege (or Developer Mode) to
        // create a symlink; skip rather than fail where that's unavailable -
        // this is the documented limit, not a silently green claim (hard
        // rule 3a applied to the test suite itself).
        Path outsideDir = Files.createTempDirectory("proof-java-outside-repo");
        Path outsideFile = outsideDir.resolve("Secret.java");
        Files.writeString(outsideFile, "class Secret {}\n");

        Files.createDirectories(repoRoot.resolve("src/test/java/com/example"));
        Path link = repoRoot.resolve("src/test/java/com/example/Linked.java");
        try {
            Files.createSymbolicLink(link, outsideFile);
        } catch (UnsupportedOperationException | FileSystemException e) {
            Assumptions.abort("Symbolic links are not permitted on this machine/user: " + e.getMessage());
        }

        ModuleDefinition module = new ModuleDefinition("demo", ".", List.of(), List.of("src/test/java"));
        List<TestSourceFile> found = TestSourceScanner.scan(repoRoot, List.of(module), null);

        assertTrue(found.isEmpty(), found.toString());
    }
}
