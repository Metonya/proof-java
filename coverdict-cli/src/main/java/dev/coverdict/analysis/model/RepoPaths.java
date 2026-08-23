package dev.coverdict.analysis.model;

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
}
