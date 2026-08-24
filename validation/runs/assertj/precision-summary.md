# assertj-core canary — precision summary (M1c criterion 4, calibration round 1)

Repo: `assertj/assertj` @ `4c5ab4862668e769d0e72492f400bd919469455d`, module
`assertj-core`. Run: `verdict-no-vcs.json`, post-D-34 (AssertJ allowlist
completed by prefix), 2148 findings total. Sampled per
`docs/M0-VALIDATION-MANIFEST.md` labeling protocol (seed 42, 100-cap per
rule). Labels: `labels.csv`. Derivation script: `classify_findings.py` +
`fill_labels.py` (this directory).

## Result

| Rule | Confidence | Sampled | True-positive | Precision |
|---|---|---|---|---|
| NO_RECOGNIZED_ORACLE | HIGH | 96 | 96 | **100%** |
| NO_RECOGNIZED_ORACLE | INCONCLUSIVE | 4 | 4 | n/a (not a positive-confidence tier) |
| CATCH_ORACLE_WITHOUT_FAIL | HIGH | 2 (full population) | 2 | **100%** |

**HIGH precision requirement (≥90%) is met on the first round.** Unlike
gson (which needed two rounds - D-30 then D-31/D-32), assertj's remaining
findings after D-34's allowlist completion are overwhelmingly genuine: no
second calibration round is warranted.

## Why this differs from gson's profile

gson's false positives were almost entirely one library-recognition gap
(Google Truth, D-31). assertj's *own* test suite has a fundamentally
different shape: it is largely the unit test suite for AssertJ's **own**
internal assertion engine (`Arrays`, `Objects`, `Iterables`, `Doubles`, ...)
- calls like `arrays.assertContainsOnly(someInfo(), actual, expected);` are
void, return nothing to assert on, and the test's entire verification *is*
that the call does not throw. There is no missing library recognition here;
there genuinely is no separate oracle. This is the same shape gson's
`JsonReaderTest#testSkipVeryLongQuotedString` was in M1c-2 rounds 1/2, just
far more common in assertj's corpus (the majority of the 74/102 sampled
"genuinely oracle-less" items).

## Breakdown of the 102 reviewed (all true-positive, by category)

| Category | Count | Why it's a correct flag |
|---|---|---|
| Internal-engine call, no assertion wrapper | 74 | Genuinely oracle-less (see above) |
| Cross-file helper + lambda-wrapped real assertion | 18 | `assertThatAssertionErrorIsThrownBy`/`expectAssumptionNotMetException`/`expectAssertionError` are test-support helpers in a different file (`AssertionsUtil`, assumption base tests) - D-17 (custom/external helpers need configuration) plus the documented lambda-body-skip limit, same standard as gson's `MoreAsserts` case |
| SoftAssertions instance receiver | 2 | `softly.then(x)`/`softly.thenXxx(x)` - a real, already-backlogged gap (D-34): `isChainAnchor` is built on static (declaringType, methodName) pairs, not instance receivers |
| `WithAssertions` interface delegation | 1 | Test class `implements WithAssertions`; that interface declares its own default `assertThat(...)` overloads, so the call resolves to declaring type `org.assertj.core.api.WithAssertions`, outside D-34's prefix rule (which only covers `Assertions`/`BDDAssertions`) - **new**, distinct gap, not fixed this session |
| `InstanceOfAssertFactory` pattern | 4 | A factory builds a typed assert object (`TEMPORAL.createAssert(actual)` → `TemporalAssert`), then the terminal assertion is called directly on that object - the chain never starts from `Assertions.assertThat`/`BDDAssertions.then` at all, so `isChainAnchor` never sees it. **New**, distinct gap, not fixed this session |
| `CATCH_ORACLE_WITHOUT_FAIL` (full population) | 2 | Real `assertThat(...).withFailMessage(...).hasValue(...)` chain inside `try {} catch (AssertionError e) {}` with an empty catch, in an `@Disabled` performance-loop test - correctly flags that any assertion failure is silently swallowed |

## Two new backlog items found (not fixed this session)

Both are real, distinct allowlist/traversal gaps, structurally different
from D-31/D-33/D-34's fixes - recorded in `docs/ROADMAP.md`'s backlog:

1. **`WithAssertions` interface delegation** - a test class implementing
   `WithAssertions` gets `assertThat(...)` resolved to that interface's own
   default methods, not `Assertions`. Fix would add
   `org.assertj.core.api.WithAssertions` as another prefix-matched type.
2. **`InstanceOfAssertFactory` chains** - terminal assertions called
   directly on a factory-returned assert object, never touching
   `Assertions`/`BDDAssertions` at all. A structurally different problem
   from a missing allowlist entry - would need recognizing terminal calls
   on values statically typed as (or extending) AssertJ's own
   `AbstractAssert` hierarchy.

Both are low-volume in this sample (1 and 4 of 102 respectively) - not
urgent, but real.
