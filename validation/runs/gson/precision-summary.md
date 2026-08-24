# gson canary — precision summary (M1c criterion 4, calibration round 1)

Repo: `google/gson` @ `dae37cf0fe12235b76fb09f01118a0a8c8823f42`, module `gson`
(core). Run: `verdict-base.json` (`--base <pin>~50`), 1090 findings total.
Sampled per `docs/M0-VALIDATION-MANIFEST.md` labeling protocol (seed 42,
100-cap per rule). Labels: `labels.csv`. Derivation script:
`classify_findings.py` + `fill_gson_labels.py` (this directory).

## Result

| Rule | Confidence | Sampled | True-positive | Precision |
|---|---|---|---|---|
| NO_RECOGNIZED_ORACLE | HIGH | 96 | 4 | **4.2%** |
| NO_RECOGNIZED_ORACLE | MEDIUM | 3 | 0 | 0% |
| NO_RECOGNIZED_ORACLE | INCONCLUSIVE | 1 | 0 | n/a (not a positive-confidence tier) |
| CATCH_ORACLE_WITHOUT_FAIL | MEDIUM | 2 (full population) | 1 | 50% |

**HIGH precision requirement (≥90%) is not met.** This is documented
calibration round 1 of 2 (kill criteria, ROADMAP.md); the rule is not removed
or downgraded on a single round, and the cause below is a scoped, fixable gap,
not evidence the rule's underlying detection logic is unsound.

## Root cause (dominant, ~96% of NO_RECOGNIZED_ORACLE false positives)

gson's test suite uses **Google Truth** (`com.google.common.truth.Truth.
assertThat`/`assertWithMessage`) as its assertion library, not JUnit's own
assertions, AssertJ, Mockito, or Hamcrest. Truth is absent from the D-24
recognized-oracle allowlist. Every false positive in the sample resolves to
a real Truth assertion the engine cannot see - either called directly in the
test method, or reached correctly through the engine's own same-compilation-
unit private-helper traversal (confirmed working: `roundTrip(...)`,
`assertFormatted(...)`, `assertIncludesClass(...)`, `assertThrowsStackOverflow
(...)` all resolve to a helper that calls Truth's `assertThat`). One case
(`DefaultDateTypeAdapterTest`) chains two helper levels deep before reaching
Truth - not independently confirmed as a second gap since the Truth-miss
alone already explains it.

The `CATCH_ORACLE_WITHOUT_FAIL` false positive (`ConcurrencyTest.
testMultiThreadSerialization`) is the same root cause reached differently:
the rule spec's own documented exemption ("never fires on: oracle present
after the whole try statement") should have suppressed it, since the method
does assert the captured worker-thread error after the try - but that
assertion is also Truth, so the engine sees no recognized oracle anywhere
and the exemption never engages.

## Confirmed true positives (4/100 + 1/2)

Two are genuinely oracle-less tests (no assertion of any kind, in the method
or any same-file helper): `JsonReaderTest#testSkipVeryLongQuotedString`,
`JsonReaderTest#testBomIgnoredAsFirstCharacterOfDocument`. One is an
`@Ignore`d exploratory performance test with no assertion at all
(`PerformanceTest#testDeserializeExposedClasses`; its `CATCH_ORACLE_WITHOUT_
FAIL` sibling `testStringDeserialization` is the confirmed true positive for
that rule). One is a real tool-scope limit rather than a detector bug:
`LinkedTreeMapTest#testEqualsAndHashCode` delegates to a **cross-class**
helper (`MoreAsserts.assertEqualsAndHashCode`, a different `.java` file) -
D-17 requires custom oracle APIs to be explicit configuration, and the
engine's helper traversal is (by design) scoped to the same compilation
unit, so this is expected behavior pending the still-open M1b "custom oracle
providers" feature, not a false positive to fix here.

## Recommendation (not implemented in this session - out of M1c-2 scope)

Add Truth (`com.google.common.truth.Truth`, static `assertThat`/
`assertWithMessage`) to the D-24 recognized-oracle allowlist, the same
mechanism already used for AssertJ/Hamcrest. Re-run this same sample after
that change as calibration round 2 before drawing a kill/keep conclusion.
