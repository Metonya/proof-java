package dev.coverdict.analysis.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.AnalysisException;

/**
 * {@link GitClient} and {@link UnifiedDiffParser} are covered on their own
 * (process-invocation contract, and header-line parsing respectively); this
 * class only checks that {@link DiffAcquisition} sequences and wires them
 * correctly for both diff modes, including every failure path a bad
 * {@code --base ref} or a fresh (zero-commit) repository can produce.
 */
class DiffAcquisitionTest {

    @TempDir
    Path repo;

    @Test
    void workingTreeModeHasNoBaseIdentityAndReportsDirtyAfterAnEdit() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "Calc.java", "class Calc {\n    int x = 1;\n}\n");
        GitClient git = new GitClient(repo);

        DiffResult clean = DiffAcquisition.acquireWorkingTree(git);
        assertFalse(clean.identity().dirty());
        assertNull(clean.identity().baseRef());
        assertNull(clean.identity().base());
        assertNull(clean.identity().mergeBase());
        assertTrue(clean.changedLinesByPath().isEmpty());

        Files.writeString(repo.resolve("Calc.java"), "class Calc {\n    int x = 1;\n    int y = 2;\n}\n");
        DiffResult dirty = DiffAcquisition.acquireWorkingTree(git);
        assertTrue(dirty.identity().dirty());
        assertEquals(Set.of(3), dirty.changedLinesByPath().get("Calc.java"));
    }

    @Test
    void baseRefModeResolvesBaseAndMergeBaseSeparatelyAndDiffsToTheWorkingTree() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "Calc.java", "class Calc {\n}\n");
        runGit(repo, "checkout", "-q", "-b", "feature");
        commitFile(repo, "Calc.java", "class Calc {\n    int x = 1;\n}\n");
        // Uncommitted edit on top of the committed feature work - base-ref
        // mode must include it (diffs to the working tree, not to HEAD).
        Files.writeString(repo.resolve("Calc.java"), "class Calc {\n    int x = 1;\n    int y = 2;\n}\n");

        DiffResult result = DiffAcquisition.acquireBaseRef(new GitClient(repo), "main");

        assertEquals("main", result.identity().baseRef());
        assertEquals(runGit(repo, "rev-parse", "main").strip(), result.identity().base());
        assertEquals(runGit(repo, "merge-base", "main", "feature").strip(), result.identity().mergeBase());
        assertTrue(result.identity().dirty());
        assertEquals(Set.of(2, 3), result.changedLinesByPath().get("Calc.java"));
    }

    @Test
    void unresolvableBaseRefFailsBeforeAnyDiffIsAttempted() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");
        GitClient git = new GitClient(repo);

        AnalysisException e = assertThrows(AnalysisException.class,
            () -> DiffAcquisition.acquireBaseRef(git, "no-such-ref"));
        assertEquals("UNRESOLVABLE_REF", e.code());
    }

    @Test
    void unrelatedHistoriesIsMissingMergeBaseNotACrash() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "on-main");
        runGit(repo, "checkout", "-q", "--orphan", "unrelated");
        commitFile(repo, "b.txt", "on-unrelated");
        GitClient git = new GitClient(repo);

        AnalysisException e = assertThrows(AnalysisException.class,
            () -> DiffAcquisition.acquireBaseRef(git, "main"));
        assertEquals("MISSING_MERGE_BASE", e.code());
    }

    @Test
    void zeroCommitRepositoryFailsStructurallyNotWithANullPointer() throws IOException, InterruptedException {
        initRepo(repo); // git init, but no commits: HEAD is unborn
        GitClient git = new GitClient(repo);

        AnalysisException workingTree = assertThrows(AnalysisException.class,
            () -> DiffAcquisition.acquireWorkingTree(git));
        assertEquals("UNRESOLVABLE_HEAD", workingTree.code());

        AnalysisException baseRef = assertThrows(AnalysisException.class,
            () -> DiffAcquisition.acquireBaseRef(git, "HEAD"));
        assertEquals("UNRESOLVABLE_HEAD", baseRef.code());
    }

    private static void initRepo(Path dir) throws IOException, InterruptedException {
        runGit(dir, "init", "-q", "-b", "main");
        runGit(dir, "config", "user.email", "test@example.com");
        runGit(dir, "config", "user.name", "Test");
        runGit(dir, "config", "core.autocrlf", "false");
        runGit(dir, "config", "commit.gpgsign", "false");
    }

    private static void commitFile(Path dir, String name, String content) throws IOException, InterruptedException {
        Files.writeString(dir.resolve(name), content);
        runGit(dir, "add", "-A");
        runGit(dir, "commit", "-q", "-m", "commit " + name);
    }

    /** Test-only scaffolding: builds fixture repository state. Never used to exercise the code under test. */
    private static String runGit(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(command).directory(dir.toFile()).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed (exit " + exit + "): " + err);
        }
        return out;
    }
}
