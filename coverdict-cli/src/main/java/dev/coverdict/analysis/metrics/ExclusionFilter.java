package dev.coverdict.analysis.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.coverdict.analysis.model.ResolvedSourceFile;

/**
 * Applies {@code sonar.coverage.exclusions} glob syntax (D-05) to the
 * already-resolved, forward-slash repo-relative paths from {@link
 * dev.coverdict.analysis.binding.ModuleBinder} - once, producing the single
 * filtered dataset every metric mode is computed from (hard rule 4).
 *
 * <p>Deliberately not {@link java.nio.file.FileSystems#getPathMatcher}: that
 * API's glob behavior is platform-dependent (backslash is a literal on
 * POSIX, an escape character in some providers), which would silently
 * exclude a different set of files on Windows than on Linux for the exact
 * same config (D-22). This operates purely on normalized strings.
 *
 * <p>Syntax (matches SonarQube's {@code sonar.coverage.exclusions}): {@code
 * *} matches zero or more characters within one path segment; {@code **}
 * matches zero or more path segments; {@code ?} matches exactly one
 * character (not {@code /}).
 */
public final class ExclusionFilter {

    private ExclusionFilter() {
    }

    public static List<ResolvedSourceFile> apply(List<ResolvedSourceFile> files, List<String> globs) {
        if (globs.isEmpty()) {
            return files;
        }
        List<Pattern> patterns = compile(globs);
        List<ResolvedSourceFile> kept = new ArrayList<>();
        for (ResolvedSourceFile file : files) {
            if (!matchesAny(file.repoRelativePath(), patterns)) {
                kept.add(file);
            }
        }
        return kept;
    }

    /** Compiles once so a caller checking many paths (e.g. the changed-file classifier) never recompiles per path. */
    public static List<Pattern> compile(List<String> globs) {
        List<Pattern> patterns = new ArrayList<>(globs.size());
        for (String glob : globs) {
            patterns.add(toPattern(glob));
        }
        return patterns;
    }

    public static boolean matchesAny(String path, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private static final String REGEX_METACHARS = "\\.[]{}()+-^$|";

    static Pattern toPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        int n = glob.length();
        while (i < n) {
            char c = glob.charAt(i);
            if (c == '*') {
                i = appendStar(regex, glob, i);
            } else if (c == '?') {
                regex.append("[^/]");
                i += 1;
            } else {
                appendLiteral(regex, c);
                i += 1;
            }
        }
        return Pattern.compile(regex.toString());
    }

    /** @return the index just past the run of {@code *}/{@code **} that was consumed. */
    private static int appendStar(StringBuilder regex, String glob, int i) {
        int n = glob.length();
        boolean isDoubleStar = i + 1 < n && glob.charAt(i + 1) == '*';
        if (!isDoubleStar) {
            regex.append("[^/]*"); // single "*" - confined to one path segment
            return i + 1;
        }
        boolean hasTrailingSlash = i + 2 < n && glob.charAt(i + 2) == '/';
        if (hasTrailingSlash) {
            regex.append("(?:.*/)?"); // "**/" - zero or more whole directories
            return i + 3;
        }
        regex.append(".*"); // trailing "**" - anything, including "/"
        return i + 2;
    }

    private static void appendLiteral(StringBuilder regex, char c) {
        if (REGEX_METACHARS.indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }
}
