package dev.proofjava.analysis.vcs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.proofjava.analysis.model.RepoPaths;

/**
 * Pure text-to-data parser for {@code git diff --unified=0} output: never
 * invokes git itself (see {@link GitClient} for that), so it is fixture-
 * tested with checked-in {@code .diff} files under {@code fixtures/diff/}.
 *
 * <p>Reads only two line shapes - {@code +++ b/<path>} and {@code @@
 * -a[,b] +c[,d] @@} - deliberately ignoring the {@code diff --git a/... b/...}
 * header (ambiguous to split when both paths contain spaces) and the
 * {@code +}/{@code -} content lines ({@code --unified=0} keeps them
 * unneeded: the hunk header's own numbers are the changed-line contract).
 * A file contributes no entry at all when it has no new-side lines - a
 * deletion ({@code +++ /dev/null}), a pure rename with no content change
 * (no {@code +++} line at all), a mode-only change (same), a binary diff
 * (same), or a hunk whose new-side count is {@code 0} (pure removal) - so
 * the caller's classification step never has to special-case "present but
 * empty" versus "absent".
 */
public final class UnifiedDiffParser {

    private static final Pattern TARGET_LINE = Pattern.compile("^\\+\\+\\+ (.*)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private UnifiedDiffParser() {
    }

    public static Map<String, SortedSet<Integer>> parse(String diffText) {
        Map<String, SortedSet<Integer>> changedLines = new LinkedHashMap<>();
        String currentPath = null;
        String previousLine = "";
        for (String line : diffText.split("\r\n|\n", -1)) {
            Matcher target = TARGET_LINE.matcher(line);
            // Gated on the previous line starting with "--- ": that pairing is
            // a structural invariant of git's own header block, never content
            // - without this gate, an *added* source line that happens to
            // start with "++ " (most plausibly inside a comment) would read
            // as a false file-boundary and misattribute every following
            // hunk's line numbers to that garbage "path" instead of the real
            // file - a comment-content collision, not a security concern
            // (this parser only ever reads proof-java's own git subprocess
            // output, never attacker-supplied XML/report input).
            if (target.matches() && previousLine.startsWith("--- ")) {
                currentPath = resolveTargetPath(target.group(1));
                previousLine = line;
                continue;
            }
            if (line.startsWith("diff --git ")) {
                currentPath = null; // new file header block; the coming +++ line (if any) sets it back
            } else if (currentPath != null && line.startsWith("@@")) {
                Matcher hunk = HUNK_HEADER.matcher(line);
                if (hunk.matches()) {
                    accumulate(changedLines, currentPath, hunk);
                }
            }
            previousLine = line;
        }
        return changedLines;
    }

    private static void accumulate(Map<String, SortedSet<Integer>> changedLines, String path, Matcher hunk) {
        int newStart = Integer.parseInt(hunk.group(1));
        int newCount = hunk.group(2) == null ? 1 : Integer.parseInt(hunk.group(2));
        if (newCount == 0) {
            return; // pure removal at this hunk - nothing on the new side to record
        }
        SortedSet<Integer> lines = changedLines.computeIfAbsent(path, k -> new TreeSet<>());
        for (int n = newStart; n < newStart + newCount; n++) {
            lines.add(n);
        }
    }

    /**
     * {@code null} for a deleted file ({@code +++ /dev/null}); otherwise the
     * {@code b/} prefix is stripped (this parser's {@link GitClient} caller
     * always passes {@code --src-prefix=a/ --dst-prefix=b/} explicitly, so
     * this holds regardless of the user's own {@code diff.mnemonicPrefix} or
     * {@code diff.noprefix} config). Git appends a trailing tab to this line
     * when the path contains a space, to keep the path unambiguous without
     * quoting it; strip it along with any other trailing whitespace.
     */
    private static String resolveTargetPath(String rawTarget) {
        String trimmed = rawTarget.stripTrailing();
        if ("/dev/null".equals(trimmed)) {
            return null;
        }
        String withoutPrefix = trimmed.startsWith("b/") ? trimmed.substring(2) : trimmed;
        return RepoPaths.normalizeSeparators(withoutPrefix);
    }
}
