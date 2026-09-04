package dev.proofjava.analysis.oracle;

import dev.proofjava.analysis.model.Confidence;

/** What one rule found for one test method - {@link OracleRuleEngine} fills in the module/path/line-range/fingerprint context that's the same for every rule. */
record RuleFinding(Confidence confidence, String message) {
}
