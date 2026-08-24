# junit-framework (junit-vintage-engine) - precision summary (M1c criterion 4)

Module: `junit-vintage-engine` (self-contained `src/main/java`+`src/test/java`;
D-36 explains why this module, not `jupiter-tests`/`platform-tests`, was
chosen). Population: 51 `NO_RECOGNIZED_ORACLE` findings (full population,
below the 100-sample threshold - manifest labeling protocol). Seed 42,
labeler: claude-sonnet-5 (2026-08-24). Every finding reviewed against its
real source; see `labels.csv`.

## Per-tier precision

| Tier | true-positive | total | precision |
|---|---|---|---|
| HIGH | 1 | 1 | 100.0% |
| MEDIUM | 0 | 46 | **0.0%** |
| INCONCLUSIVE | 0 | 4 | **0.0%** |

The manifest's hard kill-criterion (HIGH >= 90%) is met, trivially (n=1).
MEDIUM and INCONCLUSIVE are reported here in full per the manifest's
"aggregate precision cannot hide a failing tier" rule - not hidden, not
averaged into a reassuring overall number.

## Root causes (all traced to two structural gaps, not scattered noise)

**A. Chain-terminal oracle recognition (42/51 findings, 82%)** -
`execute(x).allEvents().assertEventsMatchExactly(...)` etc.
(`org.junit.platform.testkit.engine.Events`'s own fluent assertion API).
AssertJ/Truth/JUnit-style recognition works because the *library* owns the
chain's **root** call (`assertThat(x)`, `then(x)`); here the root
(`execute(x)`) is the test's own private helper, and the library only owns
the chain's **terminal** link. `OracleRecognizer.isChainAnchor` only ever
inspects the root.

A same-session attempt to fix this (checking the terminal link separately)
was implemented, then **reverted** after real-world testing showed it does
not actually work: coverdict has no `--classpath` mechanism for real corpus
runs (only source-roots + JDK reflection + the file's own import
statements), and the affected junit-vintage-engine test files never
literally import `Events` by name (it only ever appears as an inferred
chain-return type, never spelled out) - so there is no resolvable *or*
import-anchorable fact connecting `.assertEventsMatchExactly(...)` back to
its owning type. The only remaining path would be a name-only heuristic
(trust `assertEventsMatch*` regardless of any type evidence) or real
classpath support (a materially larger feature, M2/M3-scale) - both out of
scope for a measurement/calibration phase. See D-36 and the backlog below.

**B/C. Overload-ambiguity in the same-file-helper fallback (8/51 findings, INCONCLUSIVE 4 + MEDIUM 4)** -
`assertYieldsNoDescriptors`/`doesNotResolve` each have two same-name
overloads in the same file. `OracleRecognizer.sameFileNameMatch` (D-33)
bails whenever a bare call's name matches more than one same-file candidate,
regardless of whether the call's own **arity** would already disambiguate
unambiguously (it does, in all 8 cases: a 1-arg call site can only mean the
1-arg overload). A real, narrower, and more mechanically fixable gap than A
- but not attempted this phase (kept in scope discipline once A's fix
attempt had already used the session's design-and-revert budget). See
`labels.csv` rows for exact per-item reasoning; two distinct manifestations
documented there (argument-type-driven ambiguity -> INCONCLUSIVE tier;
lambda-triggers-JavaParser-failure -> MEDIUM tier, no INCONCLUSIVE flag
since the call's own name isn't oracle-suggestive).

**D. Cross-file custom-oracle helper (1/51, the single HIGH true-positive)** -
`assertPreconditionViolationNotEmptyFor` lives in
`org.junit.platform.commons.test.PreconditionAssertions`, a different
module's testFixtures - D-17 territory (external helpers need explicit
`customOracles` configuration), same category as gson's `MoreAsserts` and
assertj's `AssertionsUtil`. Correctly out of scope, not a bug.

## Backlog (not fixed this phase - see docs/ROADMAP.md and D-36)

- Chain-terminal oracle recognition for libraries that own only the
  terminal link of a chain rooted at the test's own code (JUnit Platform
  Testkit's `Events`/`EventStatistics`, and potentially others) - blocked on
  real classpath support, not an allowlist edit.
- `sameFileNameMatch`'s ambiguity bailout should consider call-site arity
  before giving up on a same-file overload family.
