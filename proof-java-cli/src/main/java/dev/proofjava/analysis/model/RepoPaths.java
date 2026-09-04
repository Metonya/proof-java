package dev.proofjava.analysis.model;

/** Forward-slash repo-relative path joining, shared wherever a base and a suffix need combining (D-22: no platform Path APIs in this logic). */
public final class RepoPaths {

    private RepoPaths() {
    }

    public static String join(String base, String suffix) {
        if (base == null || base.isEmpty() || base.equals(".")) {
            return suffix;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/" + suffix;
    }

    /**
     * Converts any backslash to a forward slash (D-22, M1c criterion 7). For
     * any path this codebase constructs itself (via {@link #join}) this is
     * already true; it matters for strings that enter from outside - a
     * {@code --report}/{@code --source-roots} CLI argument, or any {@link
     * java.nio.file.Path#toString()} result, which uses the platform
     * separator and would be backslash on Windows. A literal backslash
     * inside a real filename is not a case this project supports (the
     * schema's {@code $defs/path} pattern forbids it outright).
     */
    public static String normalizeSeparators(String path) {
        return path.replace('\\', '/');
    }

    /**
     * Pure string-segment check (SECURITY-POLICY.md #4, M1c criterion 7): true
     * when a forward-slash-normalized path is absolute, or resolves outside
     * the repo root once {@code .}/{@code ..} segments are collapsed. Never
     * touches the filesystem - this must reject a JaCoCo {@code <package
     * name="../../../../etc">} value before it is ever joined with a source
     * root and handed to {@link java.nio.file.Path#resolve}.
     */
    public static boolean isEscapingRepoRoot(String normalizedPath) {
        if (normalizedPath.startsWith("/") || normalizedPath.matches("^[A-Za-z]:.*")) {
            return true;
        }
        int depth = 0;
        for (String segment : normalizedPath.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                depth--;
                if (depth < 0) {
                    return true;
                }
            } else {
                depth++;
            }
        }
        return false;
    }
}
