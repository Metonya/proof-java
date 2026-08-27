package dev.coverdict.analysis.mutation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.testapi.TestGroupConfig;
import org.pitest.util.Glob;
import org.pitest.util.Verbosity;

import dev.coverdict.analysis.subprocess.CanonicalPaths;
import dev.coverdict.analysis.subprocess.TestGlobs;

/**
 * Subprocess entry point spawned by {@link MutationRunner} (never invoked as
 * {@code coverdict}'s own main command) - L3's sibling of {@link
 * dev.coverdict.analysis.pertest.PerTestDriver}. Drives PIT's real {@code
 * EntryPoint.execute()} through to completion (unlike L2, this process is
 * never force-killed - {@link MutationRunner} waits, bounded by a budget).
 *
 * <p>{@code RETURNS} + {@code VOID_METHOD_CALLS} approximate Descartes'
 * extreme mutation using only gregor mutators PIT ships under Apache-2.0
 * (D-56, hard rule 9: Descartes/LGPL never becomes a dependency). {@code
 * setFullMutationMatrix(true)} requires {@code "XML"} in {@code
 * outputFormats} or {@code EntryPoint} throws {@code PitError} (confirmed
 * via {@code javap} on {@code EntryPoint.checkMatrixMode}) - the resulting
 * {@code mutations.xml} is written to the private temp report dir and never
 * read; {@link CoverdictMutationListener#NAME} is the second requested
 * output format, the one coverdict actually consumes.
 *
 * <p>Args (all file paths, one entry per line, to sidestep classpath-string
 * length and OS argv quirks): {@code <moduleId> <reportDir> <classpathFile>
 * <codePathsFile> <targetClassesFile>}.
 */
public final class MutationDriver {

    private static final int EXPECTED_ARG_COUNT = 6;

    private MutationDriver() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != EXPECTED_ARG_COUNT) {
            System.err.println("MutationDriver expects " + EXPECTED_ARG_COUNT + " arguments, got " + args.length);
            System.exit(1);
            return;
        }
        String moduleId = args[0];
        String reportDir = args[1];
        List<String> classPath = Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8);
        List<String> codePaths = Files.readAllLines(Path.of(args[3]), StandardCharsets.UTF_8);
        List<String> targetClasses = Files.readAllLines(Path.of(args[4]), StandardCharsets.UTF_8);
        boolean verbose = Boolean.parseBoolean(args[5]);

        System.setProperty(CoverdictMutationListener.MODULE_ID_PROPERTY, moduleId);

        ReportOptions options = new ReportOptions();
        options.setReportDir(reportDir);
        // D-69: PIT's own classpath-matching does not normalize `.`/`./`
        // segments (D-51) - an un-canonicalized entry makes the mutation
        // pre-scan silently find zero units, no error raised.
        options.setClassPathElements(CanonicalPaths.canonicalize(classPath));
        options.setCodePaths(CanonicalPaths.canonicalize(codePaths));
        options.setSourceDirs(List.of()); // no source-locating report is ever read; avoids SmartSourceLocator's NPE on a null roots collection
        options.setTargetClasses(targetClasses);
        options.setTargetTests(Glob.toGlobPredicates(TestGlobs.samePackageGlobsFor(targetClasses)));
        options.setGroupConfig(TestGroupConfig.emptyConfig()); // D-51: mandatory, else createMinionSettings() NPEs
        options.setSkipFailingTests(true);
        options.setNumberOfThreads(1); // D-52 determinism gate held at exactly this setting
        options.setMutators(List.of("RETURNS", "VOID_METHOD_CALLS"));
        options.setFullMutationMatrix(true); // M4's kill-set needs every killing test, not just the first
        options.addOutputFormats(List.of("XML", CoverdictMutationListener.NAME));
        options.setShouldCreateTimestampedReports(false);
        options.setFailWhenNoMutations(false);
        // D-64: VERBOSE is the only setting whose showMinionOutput() is true,
        // and PIT's own coverage-minion crash message tells the user to
        // enable verbose logging before reporting the problem - which had no
        // route through coverdict until --diagnostics-dir. QUIET stays the
        // default: verbose output with no log file to land in is just a
        // slower run.
        options.setVerbosity(verbose ? Verbosity.VERBOSE : Verbosity.QUIET);

        Map<String, String> env = new HashMap<>(System.getenv());
        PluginServices plugins = PluginServices.makeForContextLoader();
        EntryPoint entry = new EntryPoint();
        AnalysisResult result = entry.execute(new File(reportDir), options, plugins, env);

        Optional<Exception> error = result.getError();
        if (error.isPresent()) {
            System.err.println("MutationDriver: " + error.get().getMessage());
            System.exit(1);
        }
    }

}
