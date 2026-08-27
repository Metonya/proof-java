package dev.coverdict.analysis.subprocess;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * D-51 (docs/DECISIONS.md) documented, on the day PIT was first integrated,
 * that every path handed to {@code ReportOptions.setClassPathElements}/
 * {@code setCodePaths}/{@code setSourceDirs} must be canonicalized -
 * PIT's own classpath-matching does not normalize {@code .}/{@code ./}
 * segments, and a non-canonical entry makes the mutation pre-scan silently
 * find zero units, no error raised. That rule was never actually applied
 * anywhere in the codebase (D-69): {@code doctor --fix}'s generated
 * classpath lists write a module's own {@code target/classes} as a
 * relative, {@code ./}-prefixed path, and both PIT drivers passed it
 * through unchanged - reproduced against a real WTA module (13 real DAO
 * classes, real coverage on other layers) instantly returning "Created 0
 * mutation test units in pre scan".
 */
public final class CanonicalPaths {

    private CanonicalPaths() {
    }

    /** @return each entry resolved via {@link File#getCanonicalPath()}; a canonicalization failure (rare - e.g. a broken symlink) falls back to the absolute form rather than dropping the entry or throwing. */
    public static List<String> canonicalize(List<String> rawPaths) {
        List<String> canonical = new ArrayList<>(rawPaths.size());
        for (String raw : rawPaths) {
            canonical.add(canonicalize(raw));
        }
        return canonical;
    }

    static String canonicalize(String raw) {
        File file = new File(raw);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsoluteFile().toString();
        }
    }
}
