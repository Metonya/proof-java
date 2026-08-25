package dev.coverdict.analysis.oracle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.coverdict.analysis.metrics.ExclusionFilter;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.config.CoverdictConfig;

/**
 * Applies configured {@code suppressions} (docs/rules/README.md): a finding is
 * withheld when its rule, path and (optionally) test method all match an entry.
 *
 * <p>Suppressed findings are <strong>counted, not erased</strong>. The count
 * comes back as a {@code SUPPRESSED_FINDINGS} warning so a reader can always
 * see that something was hidden and how much - a suppression list that
 * silently shrank the findings array would be indistinguishable from the code
 * actually improving (hard rule 3a).
 *
 * <p>Glob syntax is {@link ExclusionFilter}'s, reused rather than reimplemented
 * so one config file never has two different meanings for {@code **} (and so
 * the D-22 platform-independence reasoning holds here too). The optional
 * {@code testMethodPattern} uses the same matcher against the finding's test
 * method signature.
 */
public final class SuppressionFilter {

    private SuppressionFilter() {
    }

    public static Result apply(List<Finding> findings, List<CoverdictConfig.Suppression> suppressions) {
        if (suppressions.isEmpty() || findings.isEmpty()) {
            return new Result(findings, 0);
        }
        List<Compiled> compiled = new ArrayList<>(suppressions.size());
        for (CoverdictConfig.Suppression s : suppressions) {
            compiled.add(new Compiled(
                s.rule(),
                ExclusionFilter.compile(List.of(s.pathGlob())),
                s.testMethodPattern() == null ? null : ExclusionFilter.compile(List.of(s.testMethodPattern()))));
        }

        List<Finding> kept = new ArrayList<>(findings.size());
        int suppressed = 0;
        for (Finding finding : findings) {
            if (isSuppressed(finding, compiled)) {
                suppressed++;
            } else {
                kept.add(finding);
            }
        }
        return new Result(List.copyOf(kept), suppressed);
    }

    private static boolean isSuppressed(Finding finding, List<Compiled> compiled) {
        for (Compiled entry : compiled) {
            if (!entry.rule().equals(finding.rule())) {
                continue;
            }
            if (!ExclusionFilter.matchesAny(finding.path(), entry.pathPatterns())) {
                continue;
            }
            if (entry.methodPatterns() != null
                && (finding.testMethod() == null
                    || !ExclusionFilter.matchesAny(finding.testMethod(), entry.methodPatterns()))) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** @param suppressedCount how many findings were withheld; 0 means the warning is not emitted at all. */
    public record Result(List<Finding> findings, int suppressedCount) {
    }

    private record Compiled(String rule, List<Pattern> pathPatterns, List<Pattern> methodPatterns) {
    }
}
