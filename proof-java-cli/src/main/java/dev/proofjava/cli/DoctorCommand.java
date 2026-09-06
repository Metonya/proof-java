package dev.proofjava.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import dev.proofjava.config.ConfigLoader;
import dev.proofjava.doctor.BuildLayout;
import dev.proofjava.doctor.ClasspathFixer;
import dev.proofjava.doctor.ConfigWriter;
import dev.proofjava.doctor.DoctorDiagnostics;
import dev.proofjava.doctor.DoctorReportRenderer;
import dev.proofjava.doctor.GradleClasspathFixer;
import dev.proofjava.doctor.GradleClient;
import dev.proofjava.doctor.GradleProjectScanner;
import dev.proofjava.doctor.MavenModule;
import dev.proofjava.doctor.MavenProjectScanner;
import dev.proofjava.doctor.ModuleDiagnosis;

/**
 * Diagnoses a Maven or Gradle repository's readiness for {@code analyze}
 * before any evidence collection starts - the concrete answer to the WTA
 * dogfood's recurring setup friction (docs/ROADMAP.md, "L2/L3 classpath UX
 * gap"): three of four rounds lost time to a build/classpath problem
 * discovered only after a PIT subprocess had already started, not before.
 *
 * <p>Maven modules are found first (a project with both a {@code pom.xml}
 * and a Gradle wrapper is vanishingly rare and Maven has priority when it
 * happens); Gradle modules ({@link GradleProjectScanner}) are only scanned
 * when no Maven module was found (D-96). Read-only by default. {@code
 * --fix} additionally regenerates any missing or empty L2/L3 classpath
 * list via a real build-tool call - {@code mvn dependency:build-classpath}
 * ({@link ClasspathFixer}) for Maven, a {@code --init-script}-driven
 * {@code gradlew} invocation ({@link GradleClasspathFixer}) for Gradle -
 * the one place in proof-java that shells out to a build tool, deliberately
 * confined to this opt-in command (D-65).
 */
@Command(name = "doctor", mixinStandardHelpOptions = true,
    description = "Diagnose a Maven or Gradle repository's readiness for 'analyze' and suggest the invocation.")
class DoctorCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--repo", description = "Repository root. Default: current working directory.")
    private String repoOption;

    @Option(names = "--fix", description = "Regenerate missing or empty L2/L3 classpath lists via a real build-tool call ('mvn dependency:build-classpath' for Maven, a gradlew --init-script for Gradle).")
    private boolean fix;

    @Option(names = "--write-config", description = "Write every usable module's binding to proof.config.json (D-66), so a rerun of 'analyze' needs no --module/--report flags at all.")
    private boolean writeConfig;

    /** One discovered module, paired with the build-tool layout that found it - the only fact {@link DoctorDiagnostics}/the {@code --fix} dispatch need beyond the module itself. */
    private record DiscoveredModule(MavenModule module, BuildLayout layout) {
    }

    @Override
    public Integer call() {
        Path repoRoot = Path.of(repoOption != null ? repoOption : System.getProperty("user.dir"));
        List<DiscoveredModule> discovered = discoverModules(repoRoot);

        if (discovered.isEmpty()) {
            printErr("proof-java: no Maven or Gradle module found under " + repoRoot + "." + noModuleHint(repoRoot));
            return ExitCode.INCOMPLETE.value();
        }

        if (fix) {
            applyFixes(repoRoot, discovered);
        }

        List<ModuleDiagnosis> diagnoses = discovered.stream()
            .map(dm -> DoctorDiagnostics.diagnose(repoRoot, dm.module(), dm.layout()))
            .toList();

        if (writeConfig) {
            writeConfigFile(repoRoot, diagnoses);
        }

        // System.exit() (Main.main) can otherwise cut the process before an
        // autoFlush PrintWriter's buffer is drained - found live while
        // building this command: identical output vanished entirely without
        // an explicit flush, every single run, no exception raised.
        spec.commandLine().getOut().print(DoctorReportRenderer.render(diagnoses));
        spec.commandLine().getOut().flush();

        boolean anyBlocker = diagnoses.stream().anyMatch(ModuleDiagnosis::hasBlocker);
        return anyBlocker ? ExitCode.INCOMPLETE.value() : ExitCode.COMPLETE.value();
    }

    private static List<DiscoveredModule> discoverModules(Path repoRoot) {
        List<MavenModule> mavenModules = MavenProjectScanner.scan(repoRoot);
        if (!mavenModules.isEmpty()) {
            return mavenModules.stream().map(m -> new DiscoveredModule(m, BuildLayout.MAVEN)).toList();
        }
        List<MavenModule> gradleModules = GradleProjectScanner.scan(repoRoot);
        return gradleModules.stream().map(m -> new DiscoveredModule(m, BuildLayout.GRADLE)).toList();
    }

    /**
     * Fixes are attempted for every module whose classpath is missing or
     * broken today - re-diagnosing after each fix would double the build-tool
     * calls for no benefit, so {@link #call} re-diagnoses once, after every
     * fix attempt is done.
     */
    private void applyFixes(Path repoRoot, List<DiscoveredModule> discovered) {
        for (DiscoveredModule dm : discovered) {
            ModuleDiagnosis before = DoctorDiagnostics.diagnose(repoRoot, dm.module(), dm.layout());
            if (before.perTestClasspath() != null && before.mutationClasspath() != null) {
                continue; // already usable - a real build-tool call here would only cost time
            }
            printErr("proof-java: doctor: fixing classpath for '" + dm.module().id() + "'...");
            ClasspathFixer.FixResult result = dm.layout() == BuildLayout.GRADLE
                ? fixGradle(repoRoot, dm.module())
                : ClasspathFixer.fix(repoRoot, dm.module());
            if (!result.ok()) {
                printErr("proof-java: doctor: '" + dm.module().id() + "' - " + result.problem());
            }
        }
    }

    private static ClasspathFixer.FixResult fixGradle(Path repoRoot, MavenModule module) {
        if (!new GradleClient(repoRoot).hasWrapper()) {
            return new ClasspathFixer.FixResult(false, "no Gradle wrapper (gradlew) found at " + repoRoot
                + " - proof-java only ever invokes a project's own committed wrapper");
        }
        return GradleClasspathFixer.fix(repoRoot, module);
    }

    private void writeConfigFile(Path repoRoot, List<ModuleDiagnosis> diagnoses) {
        Path target = repoRoot.resolve(ConfigLoader.DEFAULT_FILE_NAME);
        boolean wrote = ConfigWriter.write(diagnoses, target);
        if (wrote) {
            printErr("proof-java: doctor: wrote " + ConfigLoader.DEFAULT_FILE_NAME);
        } else {
            printErr("proof-java: doctor: no module has a usable report yet - " + ConfigLoader.DEFAULT_FILE_NAME
                + " not written");
        }
    }

    /**
     * D-96: a Gradle-only repository used to get the same bare "no Maven
     * module found" message as a directory with no build file at all - no
     * hint that anything Gradle-specific was even seen. {@code doctor} now
     * discovers real Gradle modules (see {@link #discoverModules}), so this
     * only fires for the narrower remaining case: a Gradle marker file
     * exists but no module could actually be resolved from it (no {@code
     * include(...)} subprojects, and the root project has no {@code
     * src/main/java}/{@code src/test/java} of its own).
     */
    private static String noModuleHint(Path repoRoot) {
        boolean hasSettings = java.nio.file.Files.exists(repoRoot.resolve("settings.gradle"))
            || java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"));
        boolean hasBuildFile = java.nio.file.Files.exists(repoRoot.resolve("build.gradle"))
            || java.nio.file.Files.exists(repoRoot.resolve("build.gradle.kts"));
        if (!hasSettings && !hasBuildFile) {
            return "";
        }
        return " A Gradle " + (hasSettings ? "settings" : "build") + " file is present, but no module could be "
            + "resolved from it - no 'include(...)' subprojects were found, and the root project has no "
            + "src/main/java or src/test/java of its own.";
    }

    private void printErr(String message) {
        spec.commandLine().getErr().println(message);
        spec.commandLine().getErr().flush();
    }
}
