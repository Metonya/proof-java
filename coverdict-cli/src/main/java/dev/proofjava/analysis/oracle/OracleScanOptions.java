package dev.proofjava.analysis.oracle;

import java.util.List;

import com.github.javaparser.resolution.TypeSolver;

import dev.proofjava.config.CoverdictConfig;

/**
 * Everything {@link OracleRuleEngine#scan} needs beyond the repo, its modules
 * and the diff scope. Introduced when {@code customOracles} and
 * {@code suppressions} arrived (D-40/D-41) and the positional signature had
 * already reached seven parameters - three of which were optional hooks whose
 * order nothing but the compiler was checking.
 *
 * @param extraTypeSolvers jar solvers from {@code --classpath}
 *                         ({@link ClasspathLoader}), or the fixture harness's
 *                         jars in tests. Empty is the normal case, not a
 *                         degraded one (D-28's import-anchoring tier).
 * @param customOracles    configured {@code Type#methodPattern} oracle entries
 * @param suppressions     configured suppressions; matching findings are
 *                         withheld from the list but counted in a
 *                         {@code SUPPRESSED_FINDINGS} warning, never dropped
 *                         silently (hard rule 3a)
 * @param findingsCap      SECURITY-POLICY.md #2; test-only override exists so
 *                         truncation can be exercised without a 10,000-method
 *                         fixture
 */
public record OracleScanOptions(
    List<TypeSolver> extraTypeSolvers,
    List<String> customOracles,
    List<CoverdictConfig.Suppression> suppressions,
    int findingsCap) {

    /** SECURITY-POLICY.md #2: 10,000 findings per run. */
    public static final int DEFAULT_FINDINGS_CAP = 10_000;

    public static OracleScanOptions defaults() {
        return new OracleScanOptions(List.of(), List.of(), List.of(), DEFAULT_FINDINGS_CAP);
    }

    public OracleScanOptions withTypeSolvers(List<TypeSolver> solvers) {
        return new OracleScanOptions(solvers, customOracles, suppressions, findingsCap);
    }

    public OracleScanOptions withConfig(CoverdictConfig config) {
        return new OracleScanOptions(extraTypeSolvers, config.customOracles(), config.suppressions(), findingsCap);
    }

    public OracleScanOptions withFindingsCap(int cap) {
        return new OracleScanOptions(extraTypeSolvers, customOracles, suppressions, cap);
    }
}
