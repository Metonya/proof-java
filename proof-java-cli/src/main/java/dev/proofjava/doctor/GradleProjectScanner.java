package dev.proofjava.doctor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.proofjava.analysis.model.RepoPaths;

/**
 * Reads a Gradle build's {@code settings.gradle(.kts)} to discover the same
 * module id/root pairs {@link MavenProjectScanner} discovers from a Maven
 * reactor's {@code <modules>} - the Gradle-support counterpart D-96's
 * {@code doctor} hint pointed at ("'doctor' does not discover Gradle
 * modules... yet").
 *
 * <p>Deliberately a regex scan over the settings file's text, not a real
 * Groovy/Kotlin-script parser: {@code settings.gradle(.kts)} is executable
 * code, not structured data the way {@code pom.xml} is, and actually
 * running it (a real {@code gradle projects} invocation) is exactly the
 * kind of "shell out to the build tool" step this class keeps read-only and
 * confined to {@code doctor --fix} (D-65) instead - see {@link
 * GradleClasspathFixer}. This scan handles the common, literal {@code
 * include(":a", ":b:c")} / {@code include ':a'} shapes; anything computed
 * (a loop building the include list, {@code file(...)} project-dir
 * overrides) is silently invisible here, same "best-effort, never fails
 * the whole scan" posture as a malformed {@code pom.xml} branch in {@link
 * MavenProjectScanner}.
 */
public final class GradleProjectScanner {

    /**
     * Deliberately matched per line (see {@link #scan}), not against the
     * whole file with a repeated group - a single regex like {@code
     * (?:['"][^'"]+['"]\s*,?\s*)+} over an unbounded string is exactly the
     * nested-quantifier shape that risks catastrophic backtracking on a
     * pathological input (SonarQube java:S5852). Real {@code include(...)}
     * calls are conventionally one line each; scanning line by line keeps
     * every match linear in that line's own length and costs nothing for
     * the common case.
     *
     * <p>Matches plain {@code include(} and any same-purpose wrapper
     * function whose name starts with {@code include} - found necessary
     * against a real repo, not a hypothetical: junit-framework's own
     * {@code settings.gradle.kts} declares every real module through its
     * own {@code includeProject(name, ...)} helper, never a bare {@code
     * include(...)} call.
     *
     * <p>Two Gradle APIs are deliberately excluded, each for its own
     * reason:
     * <ul>
     *   <li>{@code includeBuild(...)} - a composite build is a separate
     *       Gradle root with its own lifecycle, not a subproject of this
     *       one, and treating it as such would generate a wrong (and
     *       wrongly nested) Gradle task path in {@link
     *       GradleClasspathFixer}.</li>
     *   <li>{@code includeFlat(...)} - names a project whose directory is
     *       a <em>sibling</em> of the root project ({@code ../name}), not
     *       a subdirectory. That is outside the repository root, which
     *       this codebase's repo-relative path model cannot represent at
     *       all ({@code RepoPaths.isEscapingRepoRoot}). Reporting it as
     *       the plain subdirectory {@code name} - what this scan used to
     *       do - names a directory that is either absent or, worse, some
     *       unrelated real directory. Skipping it is the honest outcome:
     *       a module this tool cannot address is better absent than
     *       wrong.</li>
     * </ul>
     *
     * <p>{@code includeGroup}/{@code includeModule}/{@code includeVersion}
     * (and their {@code ByRegex}/{@code AndSubgroups} variants) are excluded
     * for a third reason: they are {@code RepositoryContentDescriptor}
     * methods, used inside {@code repositories { content { } }} to filter
     * which artifacts a repository may serve - nothing to do with projects.
     * Found the hard way on Google's own Now in Android repo, whose
     * settings file contains {@code includeGroupByRegex("com\\.android.*")}:
     * that was read as a project, and the resulting path crashed the whole
     * command on Windows, where {@code *} is not legal in a file name.
     *
     * <p>The exclusions are separate lookaheads on purpose. A user-defined
     * wrapper whose name merely starts with an excluded word
     * ({@code includeFlattenedModules(...)}) must still be matched - the
     * {@code \b} after {@code Build}/{@code Flat} is what keeps it matched,
     * since there is no word boundary inside {@code Flattened}. The three
     * repository-content names are matched more broadly (no {@code \b}),
     * because every one of their real variants continues the word
     * ({@code includeGroupByRegex}); a project-include wrapper named
     * exactly {@code includeGroup...}/{@code includeModule...}/{@code
     * includeVersion...} would be missed, which is the safer way to be
     * wrong.
     */
    private static final Pattern INCLUDE_KEYWORD =
        Pattern.compile("\\binclude(?!Build\\b)(?!Flat\\b)(?!Group)(?!Module)(?!Version)[A-Za-z]*\\b");
    private static final Pattern QUOTED_ARG = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern ROOT_PROJECT_NAME = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");

    private GradleProjectScanner() {
    }

    /** @return every module found (root project first, if it looks like a real Java module, then each declared subproject), in declaration order. */
    public static List<MavenModule> scan(Path repoRoot) {
        Path settingsFile = findSettingsFile(repoRoot);
        if (settingsFile == null) {
            // A settings file is optional for a single-project Gradle build -
            // Gradle itself synthesizes the root project name from the
            // directory in that case. Mirror that: a bare build.gradle(.kts)
            // with no settings file is still one real module.
            return singleModuleFromBuildFileOrEmpty(repoRoot);
        }

        String text;
        try {
            text = Files.readString(settingsFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of(); // best-effort: an unreadable settings file just yields no modules
        }

        List<MavenModule> modules = new ArrayList<>();
        addRootModuleIfReal(repoRoot, text, modules);

        Set<String> gradlePaths = new LinkedHashSet<>();
        for (String line : text.split("\n", -1)) {
            if (!INCLUDE_KEYWORD.matcher(line).find()) {
                continue;
            }
            Matcher arg = QUOTED_ARG.matcher(line);
            while (arg.find()) {
                gradlePaths.add(arg.group(1));
            }
        }

        for (String gradlePath : gradlePaths) {
            String relative = gradlePath.startsWith(":") ? gradlePath.substring(1) : gradlePath;
            String normalized = RepoPaths.normalizeSeparators(relative.replace(':', '/'));
            if (normalized.isEmpty() || RepoPaths.isEscapingRepoRoot(normalized) || !isPlausibleDirectoryPath(normalized)) {
                continue; // blank, repo-escaping, or not something that can name a directory at all
            }
            String id = lastSegment(gradlePath);
            modules.add(new MavenModule(id, normalized));
        }

        return List.copyOf(modules);
    }

    /**
     * The root project has no {@code <modules>}-style "this is an
     * aggregator" tag the way a Maven {@code packaging=pom} does, so the
     * closest read-only equivalent is: does it actually have Java sources
     * of its own? A pure umbrella build (all real code under subprojects)
     * would otherwise be reported as a module with nothing under {@code
     * src/main/java} - a check that would only ever fire as noise.
     */
    private static void addRootModuleIfReal(Path repoRoot, String settingsText, List<MavenModule> modules) {
        boolean hasOwnSources = Files.isDirectory(repoRoot.resolve("src/main/java"))
            || Files.isDirectory(repoRoot.resolve("src/test/java"));
        if (!hasOwnSources) {
            return;
        }
        Matcher rootName = ROOT_PROJECT_NAME.matcher(settingsText);
        String id = rootName.find() ? rootName.group(1) : directoryNameOrRoot(repoRoot);
        modules.add(new MavenModule(id, "."));
    }

    private static List<MavenModule> singleModuleFromBuildFileOrEmpty(Path repoRoot) {
        boolean hasBuildFile = Files.isRegularFile(repoRoot.resolve("build.gradle.kts"))
            || Files.isRegularFile(repoRoot.resolve("build.gradle"));
        if (!hasBuildFile) {
            return List.of();
        }
        return List.of(new MavenModule(directoryNameOrRoot(repoRoot), "."));
    }

    /** Gradle's own fallback when {@code rootProject.name} is never set: the containing directory's name, or the literal {@code "root"} for a filesystem root with no name segment of its own. */
    private static String directoryNameOrRoot(Path repoRoot) {
        Path fileName = repoRoot.getFileName();
        return fileName != null ? fileName.toString() : "root";
    }

    /**
     * A quoted string on an {@code include}-ish line is not automatically a
     * directory name. A glob or regex character means whatever was matched
     * is something else entirely - a dependency filter, a version pattern -
     * and on Windows those characters are not even legal in a path, so
     * resolving one throws {@link java.nio.file.InvalidPathException} and
     * takes the whole {@code doctor} run down. That is exactly what
     * happened on Google's Now in Android before the keyword pattern above
     * learned about repository-content filters; this check is the second
     * layer, so a settings file this scan misreads can only ever cost a
     * missing module, never the command.
     */
    private static boolean isPlausibleDirectoryPath(String normalizedPath) {
        for (char c : normalizedPath.toCharArray()) {
            if ("*?\"<>|".indexOf(c) >= 0 || c < 0x20) {
                return false;
            }
        }
        return true;
    }

    private static String lastSegment(String gradlePath) {
        int lastColon = gradlePath.lastIndexOf(':');
        return lastColon < 0 ? gradlePath : gradlePath.substring(lastColon + 1);
    }

    private static Path findSettingsFile(Path repoRoot) {
        Path kts = repoRoot.resolve("settings.gradle.kts");
        if (Files.isRegularFile(kts)) {
            return kts;
        }
        Path groovy = repoRoot.resolve("settings.gradle");
        if (Files.isRegularFile(groovy)) {
            return groovy;
        }
        return null;
    }
}
