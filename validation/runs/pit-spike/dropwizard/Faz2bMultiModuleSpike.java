import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.testapi.TestGroupConfig;

/**
 * M2 Faz 2b spike: multi-module variant. Unions two Maven modules' class
 * dirs, source dirs, and dependency classpaths into a single ReportOptions -
 * PIT has no concept of a Maven module, only classpath/source-dir lists, so
 * a multi-module target is "give it the union" and nothing more. Not
 * shipped; validation/scripts/pit-spike.ps1 only.
 */
public final class Faz2bMultiModuleSpike {

    private static String canonical(String... parts) throws java.io.IOException {
        return new File(String.join(File.separator, parts)).getCanonicalPath();
    }

    public static void main(String[] args) throws Exception {
        String repoRoot = args[0];
        String[] moduleRoots = args[1].split(";");
        String targetClasses = args[2];
        String targetTests = args[3];
        String depClasspathFile = args[4];
        String excludedTestClasses = args.length > 5 ? args[5] : null;

        List<String> classPath = new ArrayList<>();
        List<Path> sourceDirs = new ArrayList<>();
        List<String> codePaths = new ArrayList<>();
        for (String moduleRoot : moduleRoots) {
            String mainClasses = canonical(moduleRoot, "target", "classes");
            String testClasses = canonical(moduleRoot, "target", "test-classes");
            classPath.add(mainClasses);
            classPath.add(testClasses);
            codePaths.add(mainClasses);
            sourceDirs.add(Path.of(canonical(moduleRoot, "src", "main", "java")));
            sourceDirs.add(Path.of(canonical(moduleRoot, "src", "test", "java")));
        }
        String depClasspath = java.nio.file.Files.readString(Path.of(depClasspathFile)).strip();
        for (String entry : depClasspath.split(";")) {
            classPath.add(canonical(entry));
        }

        ReportOptions options = new ReportOptions();
        options.setReportDir(canonical(moduleRoots[0], "target", "pit-reports-faz2b-multi"));
        options.setSourceDirs(sourceDirs);
        options.setClassPathElements(classPath);
        options.setCodePaths(codePaths);
        options.setTargetClasses(Arrays.asList(targetClasses.split(",")));
        options.setTargetTests(org.pitest.util.Glob.toGlobPredicates(
                Arrays.asList(targetTests.split(","))));
        options.setMutators(List.of("NULL_RETURNS"));
        options.addOutputFormats(List.of("XML"));
        options.setTimeoutConstant(10000);
        options.setFailWhenNoMutations(false);
        options.setSkipFailingTests(true);
        options.setNumberOfThreads(1);
        options.setExportLineCoverage(true);
        options.setFeatures(List.of("+coverdictspike", "-defaultcoverage"));
        options.setGroupConfig(TestGroupConfig.emptyConfig());
        if (excludedTestClasses != null) {
            options.setExcludedTestClasses(org.pitest.util.Glob.toGlobPredicates(
                    Arrays.asList(excludedTestClasses.split(","))));
        }
        options.setVerbosity(org.pitest.util.Verbosity.VERBOSE);

        Map<String, String> env = new HashMap<>(System.getenv());
        PluginServices plugins = PluginServices.makeForContextLoader();

        EntryPoint entry = new EntryPoint();
        AnalysisResult result = entry.execute(new File(repoRoot), options, plugins, env);

        if (result.getError().isPresent()) {
            System.err.println("FAILED: " + result.getError().get());
            result.getError().get().printStackTrace();
            System.exit(1);
        } else {
            System.out.println("SUCCESS: " + result.getStatistics());
        }
    }
}
