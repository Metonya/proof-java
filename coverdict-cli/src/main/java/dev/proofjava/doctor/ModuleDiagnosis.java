package dev.proofjava.doctor;

import java.util.List;

/**
 * One module's full set of {@link DoctorCheck} results, plus the two facts
 * {@link DoctorReportRenderer} needs to build a working {@code --report}
 * flag and to know whether L2/L3 classpath flags can be suggested too.
 *
 * @param jacocoReportPath   repo-relative path to the module's JaCoCo XML,
 *                           or null if none was found (module then has no
 *                           usable coverage evidence)
 * @param perTestClasspath   repo-relative path to a validated (non-empty
 *                           code-path) per-test classpath list, or null
 * @param mutationClasspath  repo-relative path to a validated mutation
 *                           classpath list, or null
 */
public record ModuleDiagnosis(MavenModule module, List<DoctorCheck> checks, String jacocoReportPath,
                               String perTestClasspath, String mutationClasspath) {

    public boolean hasBlocker() {
        return checks.stream().anyMatch(c -> c.status() == CheckStatus.BLOCKER);
    }

    public boolean usableForAnalyze() {
        return jacocoReportPath != null && !hasBlocker();
    }
}
