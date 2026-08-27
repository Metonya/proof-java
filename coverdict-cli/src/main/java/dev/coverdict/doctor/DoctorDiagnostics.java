package dev.coverdict.doctor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import dev.coverdict.analysis.model.RepoPaths;
import dev.coverdict.analysis.subprocess.ClasspathListFile;

/**
 * Runs every read-only check against one discovered {@link MavenModule}.
 * Each check exists because a real WTA dogfood round lost time to exactly
 * the failure it now catches before {@code analyze} (or a PIT subprocess)
 * ever starts - see docs/ROADMAP.md's "L2/L3 classpath UX gap" entry.
 */
public final class DoctorDiagnostics {

    private static final String JACOCO_REPORT_RELATIVE = "target/site/jacoco/jacoco.xml";
    private static final String JACOCO_REPORT_PRESENT = "JACOCO_REPORT_PRESENT";
    private static final String PER_TEST_CLASSPATH_NAME = "target/coverdict-per-test-classpath.txt";
    private static final String MUTATION_CLASSPATH_NAME = "target/coverdict-mutation-classpath.txt";

    private DoctorDiagnostics() {
    }

    public static ModuleDiagnosis diagnose(Path repoRoot, MavenModule module) {
        List<DoctorCheck> checks = new ArrayList<>();
        Path moduleRoot = repoRoot.resolve(module.root());

        checkSourceRoot(checks, moduleRoot, "src/main/java", "SOURCE_ROOT");
        checkSourceRoot(checks, moduleRoot, "src/test/java", "TEST_ROOT");
        boolean compiled = checkCompiled(checks, moduleRoot);

        String jacocoReportPath = checkJacocoReport(checks, module, moduleRoot, compiled);
        checkGeneratedSources(checks, moduleRoot);

        String perTestClasspath = checkClasspathList(checks, repoRoot, module, moduleRoot,
            PER_TEST_CLASSPATH_NAME, "PER_TEST_CLASSPATH", "--per-test-classpath");
        String mutationClasspath = checkClasspathList(checks, repoRoot, module, moduleRoot,
            MUTATION_CLASSPATH_NAME, "MUTATION_CLASSPATH", "--mutation-classpath");

        return new ModuleDiagnosis(module, List.copyOf(checks), jacocoReportPath, perTestClasspath, mutationClasspath);
    }

    private static void checkSourceRoot(List<DoctorCheck> checks, Path moduleRoot, String relative, String codePrefix) {
        Path dir = moduleRoot.resolve(relative);
        if (Files.isDirectory(dir)) {
            checks.add(DoctorCheck.ok(codePrefix + "_PRESENT", relative + " found"));
        } else {
            checks.add(DoctorCheck.warn(codePrefix + "_MISSING", relative + " not found under this module"));
        }
    }

    /** @return true if target/classes exists and is non-empty - callers need this to interpret a stale-report check meaningfully. */
    private static boolean checkCompiled(List<DoctorCheck> checks, Path moduleRoot) {
        Path classesDir = moduleRoot.resolve("target/classes");
        boolean compiled = Files.isDirectory(classesDir) && dirHasAnyFile(classesDir);
        if (compiled) {
            checks.add(DoctorCheck.ok("COMPILED", "target/classes has compiled output"));
        } else {
            checks.add(DoctorCheck.warn("NOT_COMPILED",
                "target/classes is missing or empty - run the build before analyze"));
        }
        return compiled;
    }

    /**
     * A JaCoCo report older than the module's own newest {@code .class} file
     * is exactly the "report may be older than this diff" condition
     * {@code CHANGED_LINES_ABSENT_FROM_REPORT} reports after the fact
     * (ChangedFileClassifier) - catching it here means before, not after, a
     * PIT subprocess or a whole {@code analyze} run has already spent time
     * on stale evidence.
     */
    private static String checkJacocoReport(List<DoctorCheck> checks, MavenModule module,
                                             Path moduleRoot, boolean compiled) {
        Path report = moduleRoot.resolve(JACOCO_REPORT_RELATIVE);
        if (!Files.isRegularFile(report)) {
            checks.add(DoctorCheck.blocker("JACOCO_REPORT_MISSING",
                JACOCO_REPORT_RELATIVE + " not found - run the build with coverage enabled before analyze"));
            return null;
        }
        String repoRelative = RepoPaths.join(module.root(), JACOCO_REPORT_RELATIVE);
        if (!compiled) {
            checks.add(DoctorCheck.ok(JACOCO_REPORT_PRESENT, JACOCO_REPORT_RELATIVE + " found"));
            return repoRelative;
        }
        try {
            long reportTime = Files.getLastModifiedTime(report).toMillis();
            long newestClassTime = newestFileTime(moduleRoot.resolve("target/classes"));
            if (newestClassTime > reportTime) {
                checks.add(DoctorCheck.blocker("JACOCO_REPORT_STALE",
                    JACOCO_REPORT_RELATIVE + " is older than the module's compiled output - rebuild with coverage "
                        + "before analyze, or its new-code numbers will be silently incomplete"));
            } else {
                checks.add(DoctorCheck.ok(JACOCO_REPORT_PRESENT, JACOCO_REPORT_RELATIVE + " found and up to date"));
            }
        } catch (IOException e) {
            checks.add(DoctorCheck.ok(JACOCO_REPORT_PRESENT, JACOCO_REPORT_RELATIVE + " found (freshness unverified)"));
        }
        return repoRelative;
    }

    /**
     * MapStruct/protobuf-style generated sources under a module's own
     * {@code target/generated-sources} sit outside the default {@code
     * src/main/java} root - a real WTA warning-noise source
     * ({@code MISSING_SOURCE_FILE} for {@code *Impl.java} mappers) traces
     * directly to this.
     */
    private static void checkGeneratedSources(List<DoctorCheck> checks, Path moduleRoot) {
        Path generated = moduleRoot.resolve("target/generated-sources");
        if (Files.isDirectory(generated) && dirHasAnyJavaFile(generated)) {
            checks.add(DoctorCheck.warn("GENERATED_SOURCES_FOUND",
                "target/generated-sources contains .java files not under src/main/java - add them with "
                    + "--source-roots or JaCoCo-reported classes there will warn MISSING_SOURCE_FILE"));
        }
    }

    /**
     * @return the repo-relative path to a validated classpath list, or null
     *         if none exists yet (not itself a problem - L2/L3 are opt-in)
     *         or the one found has no usable code path (a real blocker: the
     *         exact silent-empty-evidence shape the WTA dogfood hit twice).
     */
    private static String checkClasspathList(List<DoctorCheck> checks, Path repoRoot, MavenModule module,
                                               Path moduleRoot, String relativeName, String codePrefix, String flagName) {
        Path file = moduleRoot.resolve(relativeName);
        if (!Files.isRegularFile(file)) {
            checks.add(DoctorCheck.warn(codePrefix + "_MISSING",
                relativeName + " not found - only needed for " + flagName + " (L2/L3), run 'doctor --fix' to "
                    + "generate it"));
            return null;
        }
        String repoRelative = RepoPaths.join(module.root(), relativeName);
        ClasspathListFile parsed = ClasspathListFile.load(repoRoot, repoRelative);
        if (!parsed.ok()) {
            checks.add(DoctorCheck.blocker(codePrefix + "_EMPTY",
                relativeName + " exists but " + parsed.problem() + " - " + flagName
                    + " would silently collect no evidence; run 'doctor --fix' to regenerate it"));
            return null;
        }
        checks.add(DoctorCheck.ok(codePrefix + "_PRESENT",
            relativeName + " found (" + parsed.classPathElements().size() + " entries)"));
        return repoRelative;
    }

    private static boolean dirHasAnyFile(Path dir) {
        try (var stream = Files.walk(dir, 4)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean dirHasAnyJavaFile(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    private static long newestFileTime(Path dir) throws IOException {
        long[] newest = {0L};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".class")) {
                    newest[0] = Math.max(newest[0], attrs.lastModifiedTime().toMillis());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return newest[0];
    }
}
