package dev.coverdict.analysis.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.coverdict.analysis.AnalysisException;

/**
 * Exercises {@link GitClient} against a real {@code git} binary and a
 * throwaway repository built in {@code @TempDir} - the parsing logic itself
 * is covered without git in {@link UnifiedDiffParserTest}; this class is
 * about the process-invocation contract (exit codes, timeouts, encoding).
 */
class GitClientTest {

    @TempDir
    Path repo;

    @Test
    void resolveHeadReturnsTheCurrentCommitSha() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");
        String expected = runGit(repo, "rev-parse", "HEAD").strip();

        assertEquals(expected, new GitClient(repo).resolveHead());
    }

    @Test
    void resolveHeadOnAnUnbornRepositoryIsUnresolvableHead() throws IOException, InterruptedException {
        initRepo(repo); // no commits at all
        GitClient client = new GitClient(repo);

        AnalysisException e = assertThrows(AnalysisException.class, client::resolveHead);
        assertEquals("UNRESOLVABLE_HEAD", e.code());
    }

    @Test
    void resolveRefOnAMissingRefIsUnresolvableRef() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");
        GitClient client = new GitClient(repo);

        AnalysisException e = assertThrows(AnalysisException.class, () -> client.resolveRef("no-such-branch"));
        assertEquals("UNRESOLVABLE_REF", e.code());
    }

    @Test
    void resolveRefOnATagOrBranchReturnsItsCommitSha() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");
        runGit(repo, "tag", "v1");
        String expected = runGit(repo, "rev-parse", "v1").strip();

        assertEquals(expected, new GitClient(repo).resolveRef("v1"));
    }

    @Test
    void mergeBaseReturnsTheCommonAncestor() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "base");
        String base = runGit(repo, "rev-parse", "HEAD").strip();
        runGit(repo, "checkout", "-q", "-b", "feature");
        commitFile(repo, "a.txt", "feature-change");
        String head = runGit(repo, "rev-parse", "HEAD").strip();

        assertEquals(base, new GitClient(repo).mergeBase("main", head));
    }

    @Test
    void mergeBaseOnUnrelatedHistoriesIsMissingMergeBase() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "on-main");
        runGit(repo, "checkout", "-q", "--orphan", "unrelated");
        commitFile(repo, "b.txt", "on-unrelated");
        GitClient client = new GitClient(repo);

        AnalysisException e = assertThrows(AnalysisException.class,
            () -> client.mergeBase("main", "unrelated"));
        assertEquals("MISSING_MERGE_BASE", e.code());
    }

    @Test
    void isDirtyIsFalseOnACleanRepoAndTrueAfterATrackedEdit() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");
        GitClient client = new GitClient(repo);

        assertFalse(client.isDirty());

        Files.writeString(repo.resolve("a.txt"), "changed");
        assertTrue(client.isDirty());
    }

    @Test
    void diffUnified0ProducesTextThatUnifiedDiffParserResolvesToTheExpectedLines() throws IOException, InterruptedException {
        initRepo(repo);
        Files.writeString(repo.resolve("Calc.java"), "class Calc {\n    int x = 1;\n}\n");
        runGit(repo, "add", "-A");
        runGit(repo, "commit", "-q", "-m", "base");

        Files.writeString(repo.resolve("Calc.java"), "class Calc {\n    int x = 1;\n    int y = 2;\n}\n");

        String diffText = new GitClient(repo).diffUnified0("HEAD");
        Map<String, SortedSet<Integer>> parsed = UnifiedDiffParser.parse(diffText);
        assertEquals(Set.of(3), parsed.get("Calc.java"));
    }

    @Test
    void untrackedFilesListsOnlyNonIgnoredUntrackedPaths() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");
        Files.writeString(repo.resolve(".gitignore"), "ignored.txt\n");
        runGit(repo, "add", ".gitignore");
        runGit(repo, "commit", "-q", "-m", "gitignore");
        Files.writeString(repo.resolve("untracked.java"), "class Untracked {}\n");
        Files.writeString(repo.resolve("ignored.txt"), "should not appear");

        List<String> untracked = new GitClient(repo).untrackedFiles();
        assertEquals(List.of("untracked.java"), untracked);
    }

    @Test
    void aTimeoutTooShortToCompleteIsAGitTimeoutNotAHang() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");

        GitClient client = new GitClient(repo, Duration.ZERO);
        AnalysisException e = assertThrows(AnalysisException.class, client::resolveHead);
        assertEquals("GIT_TIMEOUT", e.code());
    }

    @Test
    void aDiffLargerThanTheProcessPipeBufferCompletesWithoutDeadlocking() throws IOException, InterruptedException {
        initRepo(repo);
        commitFile(repo, "a.txt", "one");

        StringBuilder bigFile = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            bigFile.append("    line ").append(i).append(" of padding content\n");
        }
        Files.writeString(repo.resolve("Big.java"), bigFile.toString());
        runGit(repo, "add", "-A");

        String diffText = new GitClient(repo, Duration.ofSeconds(30)).diffUnified0("HEAD");
        assertTrue(diffText.length() > 1_000_000, "expected a diff over 1MB, got " + diffText.length() + " bytes");
        assertTrue(diffText.contains("+++ b/Big.java"));
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
