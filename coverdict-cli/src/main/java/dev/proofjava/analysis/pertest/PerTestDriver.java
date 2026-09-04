package dev.proofjava.analysis.pertest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.testapi.TestGroupConfig;
import org.pitest.util.Glob;
import org.pitest.util.Verbosity;

import dev.proofjava.analysis.subprocess.CanonicalPaths;
import dev.proofjava.analysis.subprocess.TestGlobs;

/**
 * Subprocess entry point spawned by {@link PerTestRunner} (never invoked as
 * {@code coverdict}'s own main command). Drives PIT's real {@code
 * EntryPoint.execute()} - not a coverage-phase bypass (a direct {@code
 * DefaultCoverageGenerator} call was tried and hit an unreproduced minion
 * crash; EntryPoint is PIT's own proven calling model, D-51).
 *
 * <p>Two things make this safe to run for evidence collection rather than
 * real mutation testing: a broad {@code DEFAULTS} mutator set (a narrow set
 * like {@code NULL_RETURNS} too often finds zero mutable points on a small
 * diff-scoped target, and PIT skips the coverage phase entirely when its
 * mutation pre-scan finds nothing - verified empirically this session), and
 * {@link ProofLineExporter} writing its JSON the moment {@code
 * recordCoverage()} fires, before the mutation phase (which can hang for
 * minutes, PIT_SPIKE_PLAN note 2) has done any real work. {@link
 * PerTestRunner} does not wait for this process to exit cleanly - it polls
 * for the output file and kills the process once found or once a timeout
 * elapses, whichever comes first.
 *
 * <p>Args (all file paths, one entry per line, to sidestep classpath-string
 * length and OS argv quirks): {@code <moduleId> <reportDir> <classpathFile>
 * <codePathsFile> <targetClassesFile>}.
 */
public final class PerTestDriver {

    private PerTestDriver() {
    }

    public static void main(String[] args) throws IOException {
        String moduleId = args[0];
        String reportDir = args[1];
        List<String> classPath = Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8);
        List<String> codePaths = Files.readAllLines(Path.of(args[3]), StandardCharsets.UTF_8);
        List<String> targetClasses = Files.readAllLines(Path.of(args[4]), StandardCharsets.UTF_8);
        boolean verbose = args.length > 5 && Boolean.parseBoolean(args[5]);

        System.setProperty(ProofLineExporter.MODULE_ID_PROPERTY, moduleId);
        // D-68: the only channel available to tell the SPI-instantiated
        // exporter where to actually find the target module's class bytes -
        // this driver JVM's own -cp is coverdict's shaded jar alone (see
        // PerTestRunner), never the target repo's classes, so
        // ProofLineExporter cannot rely on the JVM's ambient classpath.
        System.setProperty(ProofLineExporter.CLASSPATH_FILE_PROPERTY, args[2]);
        // D-74: lets the exporter atomically rename its temp export onto
        // OUTPUT_FILE_NAME instead of writing that name directly - see
        // ProofLineExporter.renameIntoPlace's own javadoc for why.
        System.setProperty(ProofLineExporter.REPORT_DIR_PROPERTY, reportDir);

        ReportOptions options = new ReportOptions();
        options.setReportDir(reportDir);
        // D-69: PIT's own classpath-matching does not normalize `.`/`./`
        // segments (D-51) - an un-canonicalized entry makes the mutation
        // pre-scan silently find zero units, no error raised.
        options.setClassPathElements(CanonicalPaths.canonicalize(classPath));
        options.setCodePaths(CanonicalPaths.canonicalize(codePaths));
        options.setSourceDirs(List.of()); // no mutation report is ever produced (outputFormats=[]); avoids SmartSourceLocator's NPE on a null roots collection
        options.setTargetClasses(targetClasses);
        // D-68: was unscoped List.of("*") - under a dev/test classpath
        // (SubprocessWorkspace.ownRuntimeClasspathEntries() appends this
        // JVM's own runtime classpath to every driver's -cp), that made PIT
        // try to run every test class reachable there, not just the target
        // repo's - PlaygroundMutationIT hit this directly, discovering and
        // executing coverdict's own MainTest/PlaygroundFunctionalTest
        // alongside the fixture's real tests and blowing the 120s timeout.
        options.setTargetTests(Glob.toGlobPredicates(TestGlobs.samePackageGlobsFor(targetClasses)));
        options.setGroupConfig(TestGroupConfig.emptyConfig()); // D-51: mandatory, else createMinionSettings() NPEs
        options.setSkipFailingTests(true);
        options.setNumberOfThreads(1); // D-52: the determinism gate held at exactly this setting
        options.setMutators(List.of("DEFAULTS")); // broad on purpose - see class javadoc
        options.addOutputFormats(List.of());
        options.setExportLineCoverage(true); // gates whether CoverageExporter.recordCoverage() runs at all
        options.setShouldCreateTimestampedReports(false);
        options.setFailWhenNoMutations(false);
        options.setFeatures(List.of("+coverdictspike", "-defaultcoverage"));
        // D-64: see MutationDriver - VERBOSE is the only verbosity whose
        // showMinionOutput() is true, and it is gated on --diagnostics-dir
        // so an ordinary run stays quiet.
        options.setVerbosity(verbose ? Verbosity.VERBOSE : Verbosity.QUIET);

        Map<String, String> env = new HashMap<>(System.getenv());
        PluginServices plugins = PluginServices.makeForContextLoader();
        EntryPoint entry = new EntryPoint();
        AnalysisResult result = entry.execute(new File(reportDir), options, plugins, env);

        // No diagnostic is printed here: PerTestRunner discards this process's
        // stdout/stderr (a failed run's exit code is all it observes), so a
        // message here would go nowhere.
        if (result.getError().isPresent()) {
            System.exit(1);
        }
    }
}
