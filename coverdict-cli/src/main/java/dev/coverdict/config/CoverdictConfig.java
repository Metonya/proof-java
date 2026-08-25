package dev.coverdict.config;

import java.util.List;

/**
 * The parsed {@code coverdict.config.json} (M0-CLI-INPUT.md). Every field is
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
 * @param suppressions       never null; empty when unset
 */
public record CoverdictConfig(
    Integer languageLevel,
    String encoding,
    List<String> coverageExclusions,
    String findingsScope,
    List<String> customOracles,
    List<Suppression> suppressions) {

    public static CoverdictConfig empty() {
        return new CoverdictConfig(null, null, null, null, List.of(), List.of());
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
}
