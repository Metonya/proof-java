# SUBSUMED_TEST

**Severity INFO.** A test's mutant kill-set is a strict subset of another
test's - under the mutators this run exercised, the narrower test kills
nothing the richer one does not also kill. L3 evidence (M4, D-61); like
`PSEUDO_TESTED_METHOD`, this rule does not follow `README.md`'s shared
contract - it does not anchor on a single test method via AST traversal, it
compares two tests' mutant-kill sets from a real PIT run. Unlike
`PSEUDO_TESTED_METHOD`, it names *two* test methods in one finding
(`testMethod`/`relatedTestMethod`).

This rule supersedes an earlier design, D-46's `COVERAGE_EQUIVALENT_CANDIDATE`
(a three-stage equivalence gate requiring L2 coverage overlap, L0
assertion-structure match, and L3 kill-set match all at once). D-61 records
why: that gate fired almost exclusively on near-literal copy-paste tests
SonarQube CPD already finds for free, and its L0 requirement discarded the
one finding shape coverdict's other evidence cannot get elsewhere - two
tests that look textually different but are behaviorally identical under
mutation. `SUBSUMED_TEST` asks a directional question instead (does this
test add anything a richer test does not), needs only `--mutation-report`,
and treats L0/L2 as optional message context rather than gates.

## Fires when

`--mutation-report` collected kill-set data for the module (`Mutant.
killingTests`, populated by `setFullMutationMatrix(true)`, M5/D-56), and for
some test `t`:

- `t` killed at least one mutant (`kills(t)` non-empty), **and**
- `t` is not the sole killer of any mutant (not "essential" - see below),
  **and**
- some other test `u` exists whose kill set is a **strict superset** of
  `t`'s: `kills(t) &#8842; kills(u)`.

When more than one test qualifies as `u`, the narrowest one (fewest total
kills) is reported as the dominator - the tightest, most specific
explanation of why `t` adds nothing. A tie among equally-narrow candidates
is resolved lexicographically by test id and flagged (see Confidence).

A domination chain reports each link separately: if `kills(A) &#8834;
kills(B) &#8834; kills(C)`, both "A subsumed by B" and "B subsumed by C" are
independently true and both are emitted - not deduplicated into one finding.
A long chain in one module is exactly the scenario the firing-rate kill
criterion below watches for.

## Never fires on

- A test with an empty kill set (`kills(t) = &#8709;`) - the empty set is a
  subset of every set, so without this exclusion a test with no mutation
  evidence at all would be reported as subsumed by everything. A test that
  never kills a mutant is a different, more direct finding shape (closer to
  `NO_RECOGNIZED_ORACLE`/`PSEUDO_TESTED_METHOD` territory), not this rule's.
- An **essential** test - one that is the sole killer of at least one
  mutant. Mathematically impossible to be dominated (its kill set can never
  be a subset of another's once that mutant is excluded from every other
  test's kill set by definition), checked explicitly rather than left as an
  emergent property of the math.
- Two tests with equal kill sets - an equal-size subset-superset pair is the
  same set, not a strict superset; neither dominates the other.
- A module with no `--mutation-report` evidence, or a test id from
  `killingTests` that cannot be parsed into a class/method shape, or a
  parsed class/method that cannot be located under the module's declared
  test roots (nonstandard layout, generated test code) - all degrade to
  "skip this finding" rather than a fabricated path or line (hard rule 3a).

## Confidence

- **HIGH** — exactly one test achieves the narrowest dominator size (no tie)
  **and** the subsumed test's kill count is at least 3.
- **MEDIUM** — otherwise: either the dominator is ambiguous (a tie among
  equally-narrow candidates - weaker evidence about *which* test makes `t`
  redundant, even though the subsumption fact itself is real) or the
  subsumed test's kill count is below 3 (thin evidence - one or two shared
  mutants could be coincidental overlap rather than a meaningful pattern).
- **LOW**/**INCONCLUSIVE** — never emitted by this rule, consistent with
  `PSEUDO_TESTED_METHOD`'s precedent (no v0.1 rule uses `LOW`).

## Anchor and fingerprint

The primary anchor is the **subsumed** (redundant) test: `testMethod`/
`path`/`startLine`/`endLine` name and locate it. `relatedTestMethod`/
`relatedPath` name the dominator (schema `finding.relatedTestMethod`/
`relatedPath`, D-61 - the one rule shape needing a second test identity in
the same finding). An unresolvable dominator location degrades
`relatedPath` to `null` without dropping the finding - the subsumed test's
own identity is already fully meaningful on its own.

The fingerprint (`dev.coverdict.analysis.model.Fingerprint`, shared with
every other rule) anchors on the subsumed test's own raw kill-set test id as
its signature - stable across unrelated line-number churn and across which
specific dominator the suite happens to pick, since the underlying claim
("this test adds nothing to this run's mutation kill-set") is about the
subsumed test itself, not about a specific pair.

## Suggested action text

"Review whether both tests are needed under the mutators this run
exercised; if intentionally redundant (e.g. characterization vs.
regression), consider consolidating or documenting why both exist. Not a
deletion recommendation - a different mutator set or suite scope could show
a different result." Never suggests deletion at any confidence (hard rule
3, D-08) - D-46's equivalence-partitioned-parameterization caution carries
forward unchanged: two tests can share an identical kill-set fingerprint
while remaining genuinely distinct (e.g. boundary-value parameterization
that happens to exercise the same mutants under a narrow mutator set).

## Known limits

- **Narrow mutator set.** D-56 ships only gregor `RETURNS` +
  `VOID_METHOD_CALLS`. Subsumption measured against this narrow population
  likely **overstates** real redundancy - a richer mutator set (closer to
  Descartes' extreme mutation) would very plausibly show fewer tests as
  subsumed, since more mutants means more opportunities for a "redundant"
  test to actually be the unique killer of something. Every finding message
  is phrased as "under the mutators this run exercised," never as an
  unqualified redundancy verdict.
- **Diff-scoped, not suite-wide.** D-60's diff-scoped mutation targeting
  means a test subsumed within one diff-scoped run may not be subsumed
  against the module's full test suite and full mutant population - this
  rule only sees what one run's target-classes glob actually mutated.
- **Same-package test discovery only (D-63).** `MutationDriver` scopes
  covering-test candidates to the target class's own package (a Maven/
  Gradle convention, not coverage-verified) - a test that covers the target
  class from a different package is invisible to this run's kill-matrix
  entirely, not merely deprioritized. This is deliberate (an unscoped `"*"`
  candidate set was D-59's root cause - see below), not an oversight, but
  it is a real recall gap on an unconventional source layout.
- **D-59's process-resource-growth question is closed (D-63).** The
  original finding traced to `MutationDriver` declaring every test class in
  the module a covering-test candidate; under full-matrix mode that forced
  a full-suite coverage re-gather per target method. Fixed by the
  same-package scoping named above - `MutationRunnerIT` now completes in
  ~1.1s instead of reliably hitting its 90s budget.
- **Firing-rate unmeasured.** Suite-reduction literature finds 30-70%
  removable-by-some-definition routinely; this rule's strict-superset-only
  condition should suppress most of that, but the actual rate on a real
  corpus is unmeasured as of this milestone's initial ship - the pending
  M4 kill criterion (≥90% HIGH-confidence precision **and** a bounded
  firing rate, not a large fraction of a suite flagged at once) is the
  validation this doc anticipates, not one it can certify yet.
- **Path/line resolution is convention-based, not AST-verified.** Unlike
  the L0 rules' JavaParser traversal, `SUBSUMED_TEST` locates a test method
  by a plain text scan for `<methodName>(` in the file the standard
  Maven/Gradle package-to-directory convention resolves to - an overloaded
  test method name in the same file, or a nonstandard source layout, can
  cause the finding to be silently skipped (never a wrong line) rather than
  located precisely.
