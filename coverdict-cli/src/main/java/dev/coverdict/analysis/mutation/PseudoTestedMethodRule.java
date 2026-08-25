package dev.coverdict.analysis.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.coverdict.analysis.model.AnalysisReason;
import dev.coverdict.analysis.model.Confidence;
import dev.coverdict.analysis.model.Finding;
import dev.coverdict.analysis.model.Fingerprint;
import dev.coverdict.analysis.model.RuleIds;
import dev.coverdict.analysis.model.Severity;

/**
 * M5/D-56: a production method is covered (its tests actually reach it) but
 * every mutant generated for it survived - the tests exercise the method
 * without ever observing what it does. docs/rules/PSEUDO_TESTED_METHOD.md
 * is the spec; unlike the four L0 rules this anchors on a production
 * method, not a test method, and its evidence comes from a real PIT run
 * rather than AST traversal, so it does not slot into {@code
 * OracleRuleEngine.scanFile}'s per-test-method loop - it evaluates one
 * {@link MutationModuleEvidence} (already collected by {@link
 * MutationCollector}) after the fact instead.
 *
 * <p>A method fires only when every one of its mutants is {@code SURVIVED}
 * (a mix with {@code KILLED} means at least one test does verify the
 * method; a mix with {@code NO_COVERAGE} means the method is only
 * partially covered, silently excluded rather than guessed at). A method
 * with any mutant in a non-final status ({@code TIMED_OUT}, {@code
 * MEMORY_ERROR}, {@code RUN_ERROR}, {@code NON_VIABLE}, {@code
 * NOT_STARTED}, {@code STARTED}) never fires either, but is surfaced as a
 * {@code MUTATION_INCONCLUSIVE_STATUS} warning instead of silently dropped
 * (hard rule 3a).
 */
final class PseudoTestedMethodRule {

    static final String RULE_ID = RuleIds.PSEUDO_TESTED_METHOD;
    static final Severity SEVERITY = Severity.WARNING;
    static final String SUGGESTED_ACTION =
        "Add an assertion on this method's return value or observable side effect for at least one covering test.";

    private static final Set<String> INCONCLUSIVE_STATUSES =
        Set.of("TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR", "NON_VIABLE", "NOT_STARTED", "STARTED");
    private static final String NO_COVERAGE = "NO_COVERAGE";
    private static final String SURVIVED = "SURVIVED";

    /**
     * gregor mutator class names (D-56's RETURNS+VOID_METHOD_CALLS choice):
     * {@code MutationDetails.getMutator()} returns the mutator's fully
     * qualified class name, not a short constant - confirmed against a real
     * PIT run's log (M2 spike, entrypoint-poc/pit-run.log). Only the
     * RETURNS family gets HIGH confidence: it mutates a return value alone,
     * closer to Descartes' extreme mutation than VOID_METHOD_CALLS, which
     * only removes void calls made from within the mutated method and
     * cannot approximate extreme mutation for a void method at all.
     */
    private static final String RETURNS_MUTATOR_PACKAGE = "org.pitest.mutationtest.engine.gregor.mutators.returns.";

    private PseudoTestedMethodRule() {
    }

    static Result evaluate(String moduleId, Map<String, String> classNameToPath, MutationModuleEvidence evidence) {
        List<Finding> findings = new ArrayList<>();
        List<AnalysisReason> warnings = new ArrayList<>();
        for (MutatedMethod method : evidence.methods()) {
            evaluateMethod(moduleId, classNameToPath, method, findings, warnings);
        }
        return new Result(List.copyOf(findings), List.copyOf(warnings));
    }

    private static void evaluateMethod(String moduleId, Map<String, String> classNameToPath, MutatedMethod method,
                                        List<Finding> findings, List<AnalysisReason> warnings) {
        List<Mutant> mutants = method.mutants();
        if (mutants.isEmpty()) {
            return;
        }
        String signature = method.className() + "#" + method.methodName() + method.methodDescription();

        if (hasStatus(mutants, INCONCLUSIVE_STATUSES::contains)) {
            warnings.add(new AnalysisReason("MUTATION_INCONCLUSIVE_STATUS",
                "Module '" + moduleId + "' method '" + signature + "' has mutants in a non-final status "
                    + "(timed out, memory error, run error, non-viable, or not started); pseudo-tested "
                    + "evaluation skipped for this method.", null, moduleId));
            return;
        }
        if (hasStatus(mutants, NO_COVERAGE::equals) || !allSurvived(mutants)) {
            return;
        }

        String path = pathForClass(classNameToPath, method.className());
        if (path == null) {
            return; // could not resolve the production source path - skip rather than guess (hard rule 3a)
        }

        Confidence confidence = allReturnsMutators(mutants) ? Confidence.HIGH : Confidence.MEDIUM;
        String fingerprint = Fingerprint.compute(RULE_ID, moduleId, path, signature);
        findings.add(new Finding(RULE_ID, SEVERITY, confidence, moduleId, path, method.firstLine(), method.lastLine(),
            null, message(method), SUGGESTED_ACTION, fingerprint, signature));
    }

    private static boolean hasStatus(List<Mutant> mutants, java.util.function.Predicate<String> statusMatches) {
        for (Mutant m : mutants) {
            if (statusMatches.test(m.status())) {
                return true;
            }
        }
        return false;
    }

    private static boolean allSurvived(List<Mutant> mutants) {
        for (Mutant m : mutants) {
            if (!SURVIVED.equals(m.status())) {
                return false;
            }
        }
        return true;
    }

    private static boolean allReturnsMutators(List<Mutant> mutants) {
        for (Mutant m : mutants) {
            if (!m.mutator().startsWith(RETURNS_MUTATOR_PACKAGE)) {
                return false;
            }
        }
        return true;
    }

    /** Strips a nested-class suffix ({@code Outer$Inner} -> {@code Outer}) before the index lookup - the index is keyed by top-level source file class name. */
    private static String pathForClass(Map<String, String> classNameToPath, String className) {
        int dollar = className.indexOf('$');
        String outer = dollar < 0 ? className : className.substring(0, dollar);
        return classNameToPath.get(outer);
    }

    private static String message(MutatedMethod method) {
        return method.className() + "#" + method.methodName() + " is covered but every mutant generated for it "
            + "survived - the tests that reach it never observe its behavior.";
    }

    record Result(List<Finding> findings, List<AnalysisReason> warnings) {
    }
}
