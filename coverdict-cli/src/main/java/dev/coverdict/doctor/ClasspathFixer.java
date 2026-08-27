package dev.coverdict.doctor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.coverdict.analysis.model.RepoPaths;
import dev.coverdict.analysis.subprocess.ClasspathListFile;

/**
 * Regenerates one module's L2/L3 classpath lists via a real {@link
 * MavenClient} call, replacing the hand-run recipe the WTA dogfood runbook
 * needed (ROADMAP.md, "L2/L3 classpath UX gap"). Produces the exact shape
 * {@code --per-test-classpath}/{@code --mutation-classpath} expect: the
 * module's own {@code target/classes} and {@code target/test-classes}
 * first, then every dependency jar Maven reports for the test scope - same
 * as {@code coverdict-per-test-classpath.txt}/{@code
 * coverdict-mutation-classpath.txt} in the dogfood runbook, both files
 * carrying identical content (PIT's {@code ReportOptions} takes the two as
 * separate flags, but there is no reason for L2 and L3 to see a different
 * runtime classpath for the same module).
 */
public final class ClasspathFixer {

    static final String DEPENDENCIES_FILE = "target/coverdict-dependencies.txt";
    static final String PER_TEST_CLASSPATH_FILE = "target/coverdict-per-test-classpath.txt";
    static final String MUTATION_CLASSPATH_FILE = "target/coverdict-mutation-classpath.txt";

    private ClasspathFixer() {
    }

    public static FixResult fix(Path repoRoot, MavenModule module) {
        return fix(new MavenClient(repoRoot), repoRoot, module);
    }

    static FixResult fix(MavenClient maven, Path repoRoot, MavenModule module) {
        MavenClient.Result mavenResult = maven.buildClasspath(module.root());
        if (!mavenResult.ok()) {
            return new FixResult(false, "Maven dependency resolution failed: " + mavenResult.problem());
        }

        List<String> dependencyEntries;
        try {
            dependencyEntries = readDependencyList(repoRoot.resolve(RepoPaths.join(module.root(), DEPENDENCIES_FILE)));
        } catch (IOException e) {
            return new FixResult(false, "could not read Maven's dependency-classpath output: " + e.getMessage());
        }

        List<String> combined = new ArrayList<>();
        combined.add(RepoPaths.join(module.root(), "target/classes"));
        combined.add(RepoPaths.join(module.root(), "target/test-classes"));
        combined.addAll(dependencyEntries);

        String perTestRelative = RepoPaths.join(module.root(), PER_TEST_CLASSPATH_FILE);
        String mutationRelative = RepoPaths.join(module.root(), MUTATION_CLASSPATH_FILE);
        try {
            writeClasspathFile(repoRoot.resolve(perTestRelative), combined);
            writeClasspathFile(repoRoot.resolve(mutationRelative), combined);
        } catch (IOException e) {
            return new FixResult(false, "could not write the generated classpath list: " + e.getMessage());
        }

        // The same validation DoctorDiagnostics applies on read - a generated
        // list with zero code paths must be reported, not left to fail
        // silently the next time --mutation-report runs (hard rule 3a).
        ClasspathListFile parsed = ClasspathListFile.load(repoRoot, perTestRelative);
        if (!parsed.ok()) {
            return new FixResult(false, "generated classpath list " + perTestRelative + " still " + parsed.problem());
        }
        return new FixResult(true, null);
    }

    private static List<String> readDependencyList(Path dependenciesFile) throws IOException {
        String raw = Files.readString(dependenciesFile, StandardCharsets.UTF_8).strip();
        if (raw.isEmpty()) {
            return List.of();
        }
        return List.of(raw.split(Pattern.quote(File.pathSeparator)));
    }

    private static void writeClasspathFile(Path file, List<String> lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** @param problem null on success. */
    public record FixResult(boolean ok, String problem) {
    }
}
