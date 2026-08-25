import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.util.Glob;

/**
 * M2 Faz 2a spike: proves PIT's mutationCoverage can be driven entirely
 * programmatically via EntryPoint, with zero pom.xml/build.gradle changes
 * to the target repo. Not shipped; validation/scripts/pit-spike.ps1 only.
 */
public final class Faz2aSpike {

    private static String canonical(String... parts) throws java.io.IOException {
        return new File(String.join(File.separator, parts)).getCanonicalPath();
    }

    public static void main(String[] args) throws Exception {
        String repoRoot = args[0];
        String moduleRoot = args[1];
        String targetClasses = args[2];
        String targetTests = args[3];
        String depClasspathFile = args[4];
        String excludedTestClasses = args.length > 5 ? args[5] : null;

        String mainClasses = canonical(moduleRoot, "target", "classes");
        String testClasses = canonical(moduleRoot, "target", "test-classes");
        List<String> classPath = new java.util.ArrayList<>(List.of(mainClasses, testClasses));
        String depClasspath = java.nio.file.Files.readString(Path.of(depClasspathFile)).strip();
        for (String entry : depClasspath.split(";")) {
            classPath.add(canonical(entry));
        }

        ReportOptions options = new ReportOptions();
        options.setReportDir(canonical(moduleRoot, "target", "pit-reports-faz2a"));
        options.setSourceDirs(Arrays.asList(
                Path.of(canonical(moduleRoot, "src", "main", "java")),
                Path.of(canonical(moduleRoot, "src", "test", "java"))));
        options.setClassPathElements(classPath);
        options.setCodePaths(Collections.singletonList(mainClasses));
        options.setTargetClasses(List.of(targetClasses));
        options.setTargetTests(Glob.toGlobPredicates(List.of(targetTests)));
        if (excludedTestClasses != null) {
            options.setExcludedTestClasses(Glob.toGlobPredicates(
                    Arrays.asList(excludedTestClasses.split(","))));
        }
        options.setMutators(List.of("NULL_RETURNS"));
        options.addOutputFormats(List.of("XML"));
        options.setTimeoutConstant(10000);
        options.setFailWhenNoMutations(false);
        options.setSkipFailingTests(true);
        options.setNumberOfThreads(1);
        options.setExportLineCoverage(true);
        // -defaultcoverage: the built-in XML exporter needs commons-text, not
        // on this driver's own classpath (a Maven-plugin-only convenience);
        // irrelevant to what this spike is proving.
        options.setFeatures(List.of("+coverdictspike", "-defaultcoverage"));
        options.setGroupConfig(org.pitest.testapi.TestGroupConfig.emptyConfig());
        options.setVerbosity(org.pitest.util.Verbosity.VERBOSE);

        System.err.println("[spike] classPath=" + classPath);
        System.err.println("[spike] targetClassesFilter test on sample: "
                + options.getTargetClassesFilter().test("dev.coverdict.analysis.oracle.NullCheckOnlyRule"));

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
