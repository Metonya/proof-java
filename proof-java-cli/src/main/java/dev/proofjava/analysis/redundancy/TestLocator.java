package dev.proofjava.analysis.redundancy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.proofjava.analysis.model.ModuleDefinition;
import dev.proofjava.analysis.model.RepoPaths;

/**
 * Resolves a {@link TestIdentity.Parsed} to a repo-relative path and a
 * declaration line, for {@code SUBSUMED_TEST} findings - deliberately
 * lightweight rather than a full JavaParser/symbol-solver pass (D-10's
 * machinery, wired for oracle AST recognition, is package-private to {@code
 * dev.proofjava.analysis.oracle} and configured per-run with language
 * level/encoding this engine has no reason to also thread through). Path
 * resolution follows the standard Maven/Gradle convention (package-to-
 * directory, {@code $}-stripped for nested classes, same convention {@link
 * dev.proofjava.analysis.mutation.PseudoTestedMethodRule}'s {@code
 * pathForClass} assumes on the production side); line resolution is a plain
 * text scan for the method name followed by {@code (}, first match wins.
 *
 * <p>Both steps degrade to "unresolved" rather than guessing (hard rule 3a):
 * a nonstandard source layout, an overloaded test method the scan cannot
 * disambiguate, or a class the module's declared test roots do not contain
 * all mean {@link #locate} returns {@code null} and {@link SubsumedTestRule}
 * skips that finding rather than emit a fabricated line number.
 */
final class TestLocator {

    private TestLocator() {
    }

    static Location locate(Path repoRoot, ModuleDefinition module, TestIdentity.Parsed identity) {
        String outerClassName = stripNestedSuffix(identity.className());

        for (String testRoot : module.testRoots()) {
            Path candidate;
            try {
                candidate = repoRoot.resolve(testRoot).resolve(outerClassName.replace('.', '/') + ".java");
            } catch (java.nio.file.InvalidPathException e) {
                // Belt and braces alongside TestIdentity's own validation: a
                // name that cannot even become a path is unresolved, which this
                // class already promises to handle, not an analysis-aborting
                // crash. Windows rejects ':' outright, so a leaked test id
                // aborted the whole run there while passing on Linux.
                return null;
            }
            if (Files.isRegularFile(candidate)) {
                String repoRelative = RepoPaths.normalizeSeparators(repoRoot.relativize(candidate).toString());
                int line = findMethodLine(candidate, identity.methodName());
                if (line <= 0) {
                    return null;
                }
                return new Location(repoRelative, line);
            }
        }
        return null;
    }

    private static String stripNestedSuffix(String className) {
        int dollar = className.indexOf('$');
        return dollar < 0 ? className : className.substring(0, dollar);
    }

    /** @return the 1-based line of the first {@code <methodName>(} occurrence, or -1 if not found or the file cannot be read. */
    private static int findMethodLine(Path file, String methodName) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(");
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            return -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = pattern.matcher(lines.get(i));
            if (matcher.find()) {
                return i + 1;
            }
        }
        return -1;
    }

    record Location(String path, int line) {
    }
}
