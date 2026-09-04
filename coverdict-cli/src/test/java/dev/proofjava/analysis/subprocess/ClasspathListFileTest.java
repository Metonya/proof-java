package dev.proofjava.analysis.subprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-69: {@code doctor --fix}'s {@code ClasspathFixer} writes a module's own
 * output dirs as relative, {@code ./}-prefixed entries - real WTA logs
 * showed this made PIT's mutation pre-scan silently find zero units,
 * because D-51's canonicalization rule was never actually applied. Asserts
 * {@link ClasspathListFile#load} resolves such an entry to its canonical
 * (not merely {@code Path.resolve}d) form.
 */
class ClasspathListFileTest {

    @TempDir
    Path repoRoot;

    @Test
    void relativeDotSlashEntryResolvesToItsCanonicalForm() throws IOException {
        Path classes = repoRoot.resolve("target/classes");
        Files.createDirectories(classes);
        Path listFile = repoRoot.resolve("classpath.txt");
        Files.writeString(listFile, "./target/classes\n", StandardCharsets.UTF_8);

        ClasspathListFile result = ClasspathListFile.load(repoRoot, "classpath.txt");

        assertTrue(result.ok(), "expected a valid load: " + result.problem());
        assertEquals(1, result.codePaths().size());
        String resolved = result.codePaths().get(0);
        assertFalse(resolved.contains("." + java.io.File.separator + "."),
            "expected a canonicalized path with no '.' segments, got: " + resolved);
        assertEquals(classes.toFile().getCanonicalPath(), resolved);
    }

    @Test
    void equivalentEntriesWrittenTwoWaysDedupToOne() throws IOException {
        Path classes = repoRoot.resolve("target/classes");
        Files.createDirectories(classes);
        Path listFile = repoRoot.resolve("classpath.txt");
        Files.writeString(listFile, "./target/classes\ntarget/classes\n", StandardCharsets.UTF_8);

        ClasspathListFile result = ClasspathListFile.load(repoRoot, "classpath.txt");

        assertTrue(result.ok(), "expected a valid load: " + result.problem());
        assertEquals(1, result.classPathElements().size(),
            "expected the two equivalent entries to dedup to one: " + result.classPathElements());
    }
}
