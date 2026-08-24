#!/usr/bin/env python3
"""junit-framework precision labeling (M1c-2 phase 3, D-36): apply the
manual review decisions to labels.csv, indexed by the sample's
fingerprint-sorted order (matching _bodies.txt's [N] indices)."""
import csv

CSV = "C:/Users/Mert/Desktop/coverdict/validation/runs/junit-framework/labels.csv"
LABELER = "claude-sonnet-5 (coverdict M1c-2 junit-framework phase, 2026-08-24)"

TESTKIT_CHAIN_TERMINAL = (
    "Real oracle exists: execute(x).allEvents()/.testEvents()/.containerEvents() "
    "returns org.junit.platform.testkit.engine.Events/EventStatistics, and "
    ".assertEventsMatchExactly/.assertEventsMatchLoosely/.assertStatistics(...) "
    "is the terminal, real verification call in that chain. Not found because "
    "OracleRecognizer only checks the chain's ROOT call (here, the test's own "
    "private execute(...) helper) against the allowlist (isChainAnchor / "
    "isUnconditionalOracle) - the library only owns the TERMINAL link, not the "
    "root, unlike assertThat(x)/then(x) where the library owns the root. "
    "Structurally different from D-34's assertj gaps (allowlist entry vs. "
    "root-only chain matching) - would need per-link chain checking, not an "
    "allowlist edit. New backlog item, D-36.")

OVERLOAD_ARG_TYPE_AMBIGUOUS = (
    "Real oracle exists: this test calls the local private helper "
    "assertYieldsNoDescriptors(...), which (directly or via its "
    "single-arg overload) reaches `assertThat(engineDescriptor.getChildren())"
    ".isEmpty()`. Flagged INCONCLUSIVE because assertYieldsNoDescriptors has "
    "two same-arity-1 overloads (Class<?> and LauncherDiscoveryRequest); "
    "JavaParser can't resolve the call without resolving the argument's own "
    "type, and when that fails (testFixtures-module class not on this "
    "module's declared source roots, or a `var` whose builder-chain touches "
    "types outside them), OracleRecognizer's sameFileNameMatch fallback "
    "finds 2 name matches and correctly refuses to guess between them "
    "(D-33's documented safety behavior) rather than resolving by arity+type. "
    "Honest uncertainty, but the finding's claim (\"no oracle\") is still "
    "wrong - a real assertThat chain is reachable. Related to but distinct "
    "from the doesNotResolve family below (D-36).")

OVERLOAD_LAMBDA_AMBIGUOUS = (
    "Real oracle exists: this test calls the local private helper "
    "doesNotResolve(selector) or doesNotResolve(selector, lambda), which "
    "(directly or via the 1-arg overload delegating to the 2-arg one) reaches "
    "`assertThat(testDescriptor.getChildren()).isEmpty()` in the 2-arg "
    "overload's own body - a direct, non-lambda call, unaffected by the "
    "lambda-body-skip limit. Not found because the specific call carrying a "
    "lambda argument (doesNotResolve(selector, result -> ...)) fails "
    "JavaParser's own overload resolution, and OracleRecognizer's "
    "sameFileNameMatch fallback then sees 2 name matches (1-arg and 2-arg "
    "overloads) and bails without considering that only the 2-arg one has "
    "the right arity for this call site. A real, fixable gap in the "
    "arity-blind same-file fallback, distinct from D-33's original (single "
    "candidate) fix. New backlog item, D-36.")

CROSSFILE_HELPER = (
    "assertPreconditionViolationNotEmptyFor is a cross-file, cross-module "
    "helper (org.junit.platform.commons.test.PreconditionAssertions, in "
    "junit-platform-commons's own testFixtures) - not same-compilation-unit, "
    "so OracleRecognizer's traversal correctly does not follow it (D-17: "
    "external/custom oracle helpers need explicit customOracles "
    "configuration, not automatic cross-module traversal). Same category as "
    "gson's MoreAsserts and assertj's AssertionsUtil cases.")

# index -> (label, reason)
DECISIONS = {}

# Category A: testkit Events/EventStatistics chain-terminal (42 items)
TESTKIT_INDICES = [0, 2, 3, 4, 5, 6, 8, 9, 11, 12, 13, 14, 15, 17, 19, 20, 21,
                    22, 23, 24, 25, 26, 27, 28, 30, 31, 33, 34, 35, 36, 37, 38,
                    39, 40, 41, 43, 44, 45, 47, 48, 49, 50]
for i in TESTKIT_INDICES:
    DECISIONS[i] = ("false-positive", TESTKIT_CHAIN_TERMINAL)

# Category B: assertYieldsNoDescriptors overload family (INCONCLUSIVE tier)
for i in (1, 16, 18, 29):
    DECISIONS[i] = ("false-positive", OVERLOAD_ARG_TYPE_AMBIGUOUS)

# Category C: doesNotResolve overload family (lambda trips resolution)
for i in (7, 10, 32, 46):
    DECISIONS[i] = ("false-positive", OVERLOAD_LAMBDA_AMBIGUOUS)

# Category D: cross-file custom oracle helper (D-17), true positive
DECISIONS[42] = ("true-positive", CROSSFILE_HELPER)

with open(CSV, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

assert set(DECISIONS.keys()) == set(range(len(rows))), \
    f"missing/extra indices: expected 0..{len(rows) - 1}"

for i, row in enumerate(rows):
    label, reason = DECISIONS[i]
    row["label"] = label
    row["reason"] = reason
    row["labeler"] = LABELER

with open(CSV, "w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=rows[0].keys())
    writer.writeheader()
    writer.writerows(rows)

print(f"Labeled {len(rows)} rows.")
