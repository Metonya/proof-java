# gson canary — precision summary, calibration round 2 (D-31 Truth fix)

Same repo/module/pin as round 1 (`validation/runs/gson/precision-summary.md`).
`--base <pin>~50` re-run after adding Google Truth to the D-24 oracle
allowlist (D-31). Reviewed **all** 38 findings (population is under the
manifest's 100-per-rule cap, so no sampling this round). Labels:
`labels.csv`. Derivation script: `fill_labels.py` (this directory).

## Result

| Rule | Confidence | Reviewed | True-positive | Precision |
|---|---|---|---|---|
| NO_RECOGNIZED_ORACLE | HIGH | 35 | 33 | **94.3%** |
| NO_RECOGNIZED_ORACLE | INCONCLUSIVE | 3 | n/a (unclear) | n/a |
| CATCH_ORACLE_WITHOUT_FAIL | - | 0 (no findings this round) | - | - |

**HIGH precision requirement (≥90%) is met.** Calibration round 2 of 2
(kill criteria) closes with a pass - the rule is not removed or downgraded.

## Before / after (round 1 -> round 2)

| | Round 1 | Round 2 |
|---|---|---|
| Total findings | 1090 | 38 (-96.5%) |
| NO_RECOGNIZED_ORACLE HIGH-tier precision (sampled/reviewed) | 4.2% (4/96) | 94.3% (33/35) |
| CATCH_ORACLE_WITHOUT_FAIL findings | 2 | 0 |

## What changed and why

D-31 added Google Truth (`com.google.common.truth.Truth.assertThat`/
`assertWithMessage`) to the D-24 recognized-oracle allowlist, using the
exact same chain-anchor mechanism already used for AssertJ. This closed the
dominant cause identified in round 1 - 96 of the 100 originally-sampled
false positives and both `CATCH_ORACLE_WITHOUT_FAIL` false positives all
resolved down to this one gap.

## What remains (2 confirmed false positives, both HIGH)

Both are the **same new root cause**, distinct from Truth-recognition and
not fixed in this session: `OracleRecognizer`'s helper traversal only
descends into a called method when it resolves to a **private** declaration
in the same compilation unit
(`OracleRecognizer.java` - `decl.isPrivate() && ... == cu`). Two gson test
methods route through a **public static** helper (`DefaultTypeAdaptersTest.
testDefaultDateDeserialization` calls `assertEqualsDate`/`assertEqualsTime`
directly, and `SqlTypesGsonTest.testNullSerializationAndDeserialization`'s
own private wrapper calls a public static 2-arg overload) that itself
contains real Truth assertions - the traversal stops one hop early because
the intermediate/target method is `public`, not `private`. Recommendation
(not implemented here, new backlog item): extend the traversal to also
follow same-compilation-unit **public static** helpers, not only private
ones - a narrower, more surgical change than the Truth fix, worth its own
round.

## What remains (3 unclear/INCONCLUSIVE, unrelated cause)

All three are `CircularReferenceTest` methods calling `assertThrowsStack
Overflow(() -> ...)`, a private same-file helper that itself contains a
real oracle (`assertThrows` + Truth `assertThat` on the root cause).
JavaParser's symbol solver fails to resolve this specific call because its
argument is a lambda (a documented general limitation of overloaded-method
resolution against functional-interface parameters) - unrelated to Truth.
The engine correctly reports `INCONCLUSIVE` rather than a false `HIGH`
claim (D-17), so this is the system behaving as designed under genuine
uncertainty, not a wrong verdict - but the underlying resolution gap is
real and worth its own investigation (also a new backlog item, likely
deeper JavaParser-symbol-solver work than a quick allowlist change).

## Correction to round 1's own labeling

Round 1 labeled `PerformanceTest#testStringDeserialization`'s
`CATCH_ORACLE_WITHOUT_FAIL` finding a confirmed true positive ("real gap,
independent of the Truth allowlist gap"). That was a labeling error: the
method's try block calls `parseLongJson(...)`, a private same-file helper
containing two real Truth `assertThat` calls - the labeler (this agent, in
round 1) did not check that helper's body. In round 2 this finding no
longer fires at all - not because of a bug, but because the traversal now
sees those Truth assertions inside the try block (helper traversal already
worked correctly; only Truth-recognition was missing). Left as-is in round
1's `labels.csv` per the labeling protocol (a label is a record of what was
concluded at the time); this note is the correction of record.
