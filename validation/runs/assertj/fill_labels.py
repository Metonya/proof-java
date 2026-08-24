#!/usr/bin/env python3
"""assertj precision labeling (D-34 follow-up): apply the manual review
decisions to labels.csv, indexed by the sample's fingerprint-sorted order
(matching findings-bodies.txt's [N] indices)."""
import csv

CSV = "C:/Users/Mert/Desktop/coverdict/validation/runs/assertj/labels.csv"
LABELER = "claude-sonnet-5 (coverdict M1c-2 assertj phase, 2026-08-24)"

INTERNAL_ENGINE = (
    "Calls AssertJ's own internal assertion engine directly (e.g. "
    "arrays.assertXxx(someInfo(), ...)) with no separate assertThat/then "
    "chain anywhere in the method or a traversed helper. No assertion on a "
    "return value is possible (these calls are void) - the test's only "
    "oracle is that the call does not throw. Genuinely oracle-less by the "
    "same standard used for gson's JsonReaderTest cases in M1c-2 round 1/2.")

CROSSFILE_LAMBDA = (
    "The real assertThat/assumeThat call is inside a lambda assigned to a "
    "local variable or passed inline, and the visible oracle call "
    "(assertThatAssertionErrorIsThrownBy / expectAssumptionNotMetException / "
    "expectAssertionError) is a cross-file test-support helper (AssertionsUtil "
    "or an assumptions base-test helper), not an AssertJ API method - D-17 "
    "(custom/external helpers need explicit configuration) plus the "
    "documented lambda-body-skip limit both apply, same as gson's MoreAsserts "
    "case in M1c-1/M1c-2.")

SOFT_INSTANCE = (
    "SoftAssertions instance receiver (softly.then(...)/softly.thenXxx(...)) "
    "- a real, already-documented backlog gap (D-34): isChainAnchor is built "
    "on static (declaringType, methodName) pairs, and recognizing an instance "
    "receiver's static type is a structural OracleRecognizer change, not an "
    "allowlist edit.")

WITH_ASSERTIONS = (
    "The test class `implements WithAssertions`; WithAssertions declares its "
    "own default assertThat(...) overloads (e.g. "
    "WithAssertions.assertThat(AtomicIntegerFieldUpdater)), so the bare "
    "assertThat(...) call resolves to declaring type "
    "org.assertj.core.api.WithAssertions, not Assertions - outside D-34's "
    "prefix rule (which only matches Assertions/BDDAssertions). Real, new, "
    "distinct allowlist gap - not fixed this session, flagged for backlog.")

ASSERT_FACTORY = (
    "Verifies an InstanceOfAssertFactory: a factory object builds a typed "
    "assert instance (e.g. TEMPORAL.createAssert(actual) -> TemporalAssert), "
    "then a terminal assertion method is called directly on that returned "
    "object (result.isCloseTo(...)). The chain never starts from "
    "Assertions.assertThat/BDDAssertions.then at all, so isChainAnchor never "
    "sees it - a structurally different, new allowlist gap (would require "
    "recognizing terminal calls on any AbstractAssert-typed value, not just "
    "assertThat/then chains). Not fixed this session, flagged for backlog.")

CATCH_SWALLOWED_ASSERTION = (
    "Real assertThat(...).withFailMessage(...).hasValue(...) chain inside "
    "try { } catch (AssertionError e) { } with an empty catch body, in an "
    "@Disabled performance-loop test - CATCH_ORACLE_WITHOUT_FAIL correctly "
    "identifies that any assertion failure here is silently swallowed. "
    "Correct detection, not a false positive, even though the swallowing is "
    "intentional for this perf-loop's own purpose.")

# index -> (label, reason). Index matches findings-bodies.txt's [N].
DECISIONS = {}
for i in [2,3,5,6,7,8,9,10,11,13,14,15,16,17,18,21,22,23,24,25,26,27,29,30,31,32,
          33,34,35,36,37,38,39,41,42,47,48,49,50,51,54,55,57,58,62,64,67,68,69,
          71,73,75,77,78,79,81,82,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,
          99,100,101]:
    DECISIONS[i] = ("true-positive", INTERNAL_ENGINE)
for i in [4,12,19,20,28,40,45,46,52,56,59,60,61,63,65,66,70,80]:
    DECISIONS[i] = ("true-positive", CROSSFILE_LAMBDA)
for i in [72,74]:
    DECISIONS[i] = ("true-positive", SOFT_INSTANCE)
DECISIONS[76] = ("true-positive", WITH_ASSERTIONS)
for i in [43,44,53,83]:
    DECISIONS[i] = ("true-positive", ASSERT_FACTORY)
for i in [0,1]:
    DECISIONS[i] = ("true-positive", CATCH_SWALLOWED_ASSERTION)

if sorted(DECISIONS.keys()) != list(range(102)):
    missing = set(range(102)) - set(DECISIONS.keys())
    extra = set(DECISIONS.keys()) - set(range(102))
    raise SystemExit(f"index coverage mismatch: missing={missing} extra={extra}")

with open(CSV, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
    fieldnames = list(rows[0].keys())

# labels.csv is already sorted by fingerprint (scaffold_labels.py's order),
# matching inspect_findings.py's [N] enumeration used above.
for i, row in enumerate(rows):
    label, reason = DECISIONS[i]
    row["label"] = label
    row["reason"] = reason
    row["labeler"] = LABELER

with open(CSV, "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(rows)

from collections import Counter
print(Counter((r["rule"], r["confidence"], r["label"]) for r in rows))
