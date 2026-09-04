package dev.proofjava.config;

import java.util.List;

/**
 * The parsed {@code proof.config.json} (M0-CLI-INPUT.md). Every field is
 * nullable/empty-by-default so {@link ConfigLoader#effective} can tell "the
 * config did not say" apart from "the config said this" - the whole point of
 * the precedence rule (command line &gt; config file &gt; documented defaults).
 *
 * @param languageLevel      null when unset
 * @param encoding           null when unset
 * @param coverageExclusions null when unset (an explicitly empty list means
 *                           "no exclusions" and is NOT the same as unset)
 * @param findingsScope      null when unset
 * @param customOracles      never null; empty when unset (D-24 allowlist extension)
 * @param modules            never null; empty when unset (D-66 - the same
 *                           binding {@code --module}/{@code --report}/
 *                           {@code --per-test-classpath}/{@code
 *                           --mutation-classpath} express on the command
 *                           line). Applies only when the command line
 *                           declares zero {@code --module} flags - a single
 *                           one ignores this array entirely rather than
 *                           partially merging with it.
 * @param suppressions       never null; empty when unset
 */
public record ProofConfig(
    Integer languageLevel,
    String encoding,
    List<String> coverageExclusions,
    String findingsScope,
    List<String> customOracles,
    List<ModuleConfig> modules,
    List<Suppression> suppressions) {

    public static ProofConfig empty() {
        return new ProofConfig(null, null, null, null, List.of(), List.of(), List.of());
    }

    /**
     * @param testMethodPattern optional; null means "every test method in a
     *                          matching path"
     * @param reason            mandatory by schema - a suppression without a
     *                          recorded rationale is exactly what the M0 spec
     *                          set out to prevent
     */
    public record Suppression(String rule, String pathGlob, String testMethodPattern, String reason) {
    }

    /**
     * One {@code --module <id>=<root>} binding plus its report and L2/L3
     * classpath, in one place - the shape {@code proof-java doctor
     * --write-config} generates (D-65/D-66).
     *
     * @param sourceRoots       null means "not specified in config" -
     *                          {@code AnalyzeCommand} applies the same
     *                          {@code <root>/src/main/java} default it
     *                          would for an equivalent {@code --module}
     *                          with no {@code --source-roots}
     * @param testRoots         same null-means-default rule as sourceRoots
     * @param report            null when this module has no bound report
     *                          yet (excluded from the analyzed set with
     *                          {@code MODULE_WITHOUT_REPORT}, same as an
     *                          unbound {@code --module})
     * @param perTestClasspath  null when L2 evidence is not configured for
     *                          this module
     * @param mutationClasspath null when L3 evidence is not configured for
     *                          this module
     */
    public record ModuleConfig(String id, String root, List<String> sourceRoots, List<String> testRoots,
                                String report, String perTestClasspath, String mutationClasspath) {
    }
}
