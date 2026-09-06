package dev.proofjava.doctor;

/**
 * The handful of build-tool-specific paths {@link DoctorDiagnostics} needs
 * to know before it can check a {@link MavenModule} (a build-tool-agnostic
 * id/root pair despite its name - {@link MavenProjectScanner} and {@link
 * GradleProjectScanner} both produce it) for readiness: where compiled
 * output lands, where the JaCoCo XML report lands, and what proof-java's
 * own generated L2/L3 classpath lists are named. Everything else in {@link
 * DoctorDiagnostics} (source-root checks, the blocker/warn logic) is
 * already build-tool-agnostic - only these four paths differ between
 * Maven's {@code target/} and Gradle's {@code build/} conventions.
 */
public enum BuildLayout {

    MAVEN("target/classes", "target/site/jacoco/jacoco.xml",
        "target/proof-per-test-classpath.txt", "target/proof-mutation-classpath.txt", "target/generated-sources"),

    /**
     * XML output is off by default in Gradle's {@code jacoco} plugin
     * (unlike {@code jacoco-maven-plugin}) - {@code doctor}'s own {@code
     * gradleHintOrEmpty} (DoctorCommand) already tells a Gradle user to add
     * {@code reports { xml.required.set(true) }}, so a missing report here
     * is reported the same way a missing Maven one is, not specially.
     */
    GRADLE("build/classes/java/main", "build/reports/jacoco/test/jacocoTestReport.xml",
        "build/proof-per-test-classpath.txt", "build/proof-mutation-classpath.txt", "build/generated/sources");

    private final String compiledClassesDir;
    private final String jacocoReportRelative;
    private final String perTestClasspathRelative;
    private final String mutationClasspathRelative;
    private final String generatedSourcesDir;

    BuildLayout(String compiledClassesDir, String jacocoReportRelative,
                String perTestClasspathRelative, String mutationClasspathRelative, String generatedSourcesDir) {
        this.compiledClassesDir = compiledClassesDir;
        this.jacocoReportRelative = jacocoReportRelative;
        this.perTestClasspathRelative = perTestClasspathRelative;
        this.mutationClasspathRelative = mutationClasspathRelative;
        this.generatedSourcesDir = generatedSourcesDir;
    }

    public String compiledClassesDir() {
        return compiledClassesDir;
    }

    public String jacocoReportRelative() {
        return jacocoReportRelative;
    }

    public String perTestClasspathRelative() {
        return perTestClasspathRelative;
    }

    public String mutationClasspathRelative() {
        return mutationClasspathRelative;
    }

    public String generatedSourcesDir() {
        return generatedSourcesDir;
    }
}
