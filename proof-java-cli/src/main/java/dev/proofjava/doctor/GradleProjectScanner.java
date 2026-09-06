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

    private static final Pattern INCLUDE_CALL = Pattern.compile("include\\s*\\(?\\s*((?:['\"][^'\"]+['\"]\\s*,?\\s*)+)\\)?");
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
        Matcher includeCall = INCLUDE_CALL.matcher(text);
        while (includeCall.find()) {
            Matcher arg = QUOTED_ARG.matcher(includeCall.group(1));
            while (arg.find()) {
                gradlePaths.add(arg.group(1));
            }
        }

        for (String gradlePath : gradlePaths) {
            String relative = gradlePath.startsWith(":") ? gradlePath.substring(1) : gradlePath;
            String normalized = RepoPaths.normalizeSeparators(relative.replace(':', '/'));
            if (normalized.isEmpty() || RepoPaths.isEscapingRepoRoot(normalized)) {
                continue; // a blank or repo-escaping path is not something this scan follows
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
        String id = rootName.find() ? rootName.group(1) : repoRoot.getFileName() != null ? repoRoot.getFileName().toString() : "root";
        modules.add(new MavenModule(id, "."));
    }

    private static List<MavenModule> singleModuleFromBuildFileOrEmpty(Path repoRoot) {
        boolean hasBuildFile = Files.isRegularFile(repoRoot.resolve("build.gradle.kts"))
            || Files.isRegularFile(repoRoot.resolve("build.gradle"));
        if (!hasBuildFile) {
            return List.of();
        }
        String id = repoRoot.getFileName() != null ? repoRoot.getFileName().toString() : "root";
        return List.of(new MavenModule(id, "."));
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
