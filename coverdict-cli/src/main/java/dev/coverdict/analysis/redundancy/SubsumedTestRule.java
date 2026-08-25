package dev.coverdict.analysis.redundancy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.Fingerprint;
import dev.coverdict.analysis.model.ModuleDefinition;
import dev.coverdict.analysis.model.RuleIds;
import dev.coverdict.analysis.model.Severity;
import dev.coverdict.analysis.mutation.MutationModuleEvidence;

/**
 * D-61: turns {@link SubsumptionAnalyzer}'s pure kill-matrix results into
 * {@link Finding}s. Anchors on the subsumed (redundant) test - {@code
 * testMethod}/{@code path}/{@code startLine}/{@code endLine} name it,
 * {@code relatedTestMethod}/{@code relatedPath} name the dominator that
 * makes it redundant. A pair that cannot be located on disk (nonstandard
 * layout, unresolved raw test-id format) is skipped rather than reported
 * with a fabricated path or line (hard rule 3a) - {@link
 * dev.coverdict.analysis.mutation.PseudoTestedMethodRule}'s same
 * "skip rather than guess" precedent on the production side.
 */
final class SubsumedTestRule {

    static final String RULE_ID = RuleIds.SUBSUMED_TEST;
    static final Severity SEVERITY = Severity.INFO;

    /** D-61: below this kill count, a single dominator's win is thin evidence - MEDIUM, not HIGH, even without a tie. */
    private static final int HIGH_CONFIDENCE_MIN_KILLS = 3;

    private SubsumedTestRule() {
    }

    static List<Finding> evaluate(Path repoRoot, ModuleDefinition module, MutationModuleEvidence evidence) {
        List<Finding> findings = new ArrayList<>();
        KillMatrix.Data matrix = KillMatrix.build(evidence);
        List<SubsumptionAnalyzer.Subsumption> subsumptions = SubsumptionAnalyzer.analyze(matrix);

        for (SubsumptionAnalyzer.Subsumption subsumption : subsumptions) {
            Finding finding = toFinding(repoRoot, module, subsumption);
            if (finding != null) {
                findings.add(finding);
            }
        }
        return List.copyOf(findings);
    }

    private static Finding toFinding(Path repoRoot, ModuleDefinition module, SubsumptionAnalyzer.Subsumption subsumption) {
        TestIdentity.Parsed subsumedIdentity = TestIdentity.parse(subsumption.subsumedTest());
        if (subsumedIdentity == null) {
            return null;
        }
        TestLocator.Location subsumedLocation = TestLocator.locate(repoRoot, module, subsumedIdentity);
        if (subsumedLocation == null) {
            return null;
        }

        // The dominator's own location is enrichment on top of enrichment - a
        // finding is still fully meaningful (subsumed test named, resolved,
        // and located) without it, so an unresolved dominator path degrades
        // relatedPath to null rather than dropping the whole finding.
        TestIdentity.Parsed dominatorIdentity = TestIdentity.parse(subsumption.dominatorTest());
        TestLocator.Location dominatorLocation = dominatorIdentity == null ? null
            : TestLocator.locate(repoRoot, module, dominatorIdentity);

        Confidence confidence = confidenceFor(subsumption);
        String fingerprint = Fingerprint.compute(RULE_ID, module.id(), subsumedLocation.path(), subsumption.subsumedTest());

        return new Finding(RULE_ID, SEVERITY, confidence, module.id(), subsumedLocation.path(),
            subsumedLocation.line(), subsumedLocation.line(), subsumption.subsumedTest(),
            message(subsumption), suggestedAction(), fingerprint, null,
            subsumption.dominatorTest(), dominatorLocation == null ? null : dominatorLocation.path());
    }

    private static Confidence confidenceFor(SubsumptionAnalyzer.Subsumption subsumption) {
        if (!subsumption.ambiguousDominator() && subsumption.subsumedKillCount() >= HIGH_CONFIDENCE_MIN_KILLS) {
            return Confidence.HIGH;
        }
        return Confidence.MEDIUM;
    }

    private static String message(SubsumptionAnalyzer.Subsumption subsumption) {
        return subsumption.subsumedTest() + " kills " + subsumption.subsumedKillCount()
            + " mutant(s), all also killed by " + subsumption.dominatorTest() + " (which kills "
            + subsumption.dominatorKillCount() + ") - no unique mutation evidence under the mutators this run exercised.";
    }

    private static String suggestedAction() {
        return "Review whether both tests are needed under the mutators this run exercised; if intentionally "
            + "redundant (e.g. characterization vs. regression), consider consolidating or documenting why both exist. "
            + "Not a deletion recommendation - a different mutator set or suite scope could show a different result.";
    }
}
