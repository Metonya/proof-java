package dev.proofjava.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import dev.proofjava.config.ConfigLoader;
import dev.proofjava.doctor.ClasspathFixer;
import dev.proofjava.doctor.ConfigWriter;
import dev.proofjava.doctor.DoctorDiagnostics;
import dev.proofjava.doctor.DoctorReportRenderer;
import dev.proofjava.doctor.MavenModule;
import dev.proofjava.doctor.MavenProjectScanner;
import dev.proofjava.doctor.ModuleDiagnosis;

/**
 * Diagnoses a Maven repository's readiness for {@code analyze} before any
 * evidence collection starts - the concrete answer to the WTA dogfood's
 * recurring setup friction (docs/ROADMAP.md, "L2/L3 classpath UX gap"):
 * three of four rounds lost time to a build/classpath problem discovered
 * only after a PIT subprocess had already started, not before.
 *
 * <p>Read-only by default. {@code --fix} additionally regenerates any
 * missing or empty L2/L3 classpath list via a real {@code mvn
 * dependency:build-classpath} call ({@link ClasspathFixer}) - the one place
 * in coverdict that shells out to a build tool, deliberately confined to
 * this opt-in command (D-65).
 */
@Command(name = "doctor", mixinStandardHelpOptions = true,
    description = "Diagnose a Maven repository's readiness for 'analyze' and suggest the invocation.")
class DoctorCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--repo", description = "Repository root. Default: current working directory.")
    private String repoOption;

    @Option(names = "--fix", description = "Regenerate missing or empty L2/L3 classpath lists via a real 'mvn dependency:build-classpath' call.")
    private boolean fix;

    @Option(names = "--write-config", description = "Write every usable module's binding to coverdict.config.json (D-66), so a rerun of 'analyze' needs no --module/--report flags at all.")
    private boolean writeConfig;

    @Override
    public Integer call() {
        Path repoRoot = Path.of(repoOption != null ? repoOption : System.getProperty("user.dir"));
        List<MavenModule> modules = MavenProjectScanner.scan(repoRoot);

        if (modules.isEmpty()) {
            printErr("coverdict: no Maven module found under " + repoRoot + " (no pom.xml, or an unparsable one).");
            return ExitCode.INCOMPLETE.value();
        }

        if (fix) {
            applyFixes(repoRoot, modules);
        }

        List<ModuleDiagnosis> diagnoses = modules.stream()
            .map(m -> DoctorDiagnostics.diagnose(repoRoot, m))
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

    /**
     * Fixes are attempted for every module whose classpath is missing or
     * broken today - re-diagnosing after each fix would double the Maven
     * calls for no benefit, so {@link #call} re-diagnoses once, after every
     * fix attempt is done.
     */
    private void applyFixes(Path repoRoot, List<MavenModule> modules) {
        for (MavenModule module : modules) {
            ModuleDiagnosis before = DoctorDiagnostics.diagnose(repoRoot, module);
            if (before.perTestClasspath() != null && before.mutationClasspath() != null) {
                continue; // already usable - a real mvn call here would only cost time
            }
            printErr("coverdict: doctor: fixing classpath for '" + module.id() + "'...");
            ClasspathFixer.FixResult result = ClasspathFixer.fix(repoRoot, module);
            if (!result.ok()) {
                printErr("coverdict: doctor: '" + module.id() + "' - " + result.problem());
            }
        }
    }

    private void writeConfigFile(Path repoRoot, List<ModuleDiagnosis> diagnoses) {
        Path target = repoRoot.resolve(ConfigLoader.DEFAULT_FILE_NAME);
        boolean wrote = ConfigWriter.write(diagnoses, target);
        if (wrote) {
            printErr("coverdict: doctor: wrote " + ConfigLoader.DEFAULT_FILE_NAME);
        } else {
            printErr("coverdict: doctor: no module has a usable report yet - " + ConfigLoader.DEFAULT_FILE_NAME
                + " not written");
        }
    }

    private void printErr(String message) {
        spec.commandLine().getErr().println(message);
        spec.commandLine().getErr().flush();
    }
}
