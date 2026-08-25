# Research notes

Facts, measurements, models, and external observations the project relies on.
Prototype numbers reproduce via `prototype/demo-bank/run.sh`; modeled and
externally sourced numbers are labeled separately and are research context,
not product acceptance evidence.

## 1. Coverage metric formulas

Same test run, three definitions, three numbers:

| Mode | Result | Definition |
|---|---|---|
| `jacoco-line` | 78.9% (56/71) | line covered if any instruction ran (JaCoCo LINE counter — parity verified exactly) |
| `strict-line+branch` (prototype only; superseded by D-19) | 56.3% (40/71) | `ci > 0 && mi == 0 && mb == 0`; this accidentally added branch completeness |
| `sonar-compatible` | 72.8% (91/125) | `(CT + CF + LC) / (2B + EL)`; in JaCoCo terms `(cb + LC) / (cb + mb + EL)` |

SonarQube's headline "Coverage" blends line and condition coverage. Local
JaCoCo percentages therefore never match the SonarQube figure — different
formula, not version drift. Where `cb`/`mb` = covered/missed branches per
JaCoCo XML line attributes, `LC` = lines with `ci > 0`, `EL` = executable
lines.

## 2. Per-test coverage mechanism

JUnit 5 `TestExecutionListener` (prototype uses an equivalent mini-runner):
`reset()` before each test, `getExecutionData(true)` after, one exec file per
test. Pitfall found by measurement: calling `reset()` *after* constructing the
test instance loses field-initializer coverage
(`private final Foo foo = new Foo();`) — shifted overall coverage 76.1 → 78.9
when fixed. Reset must precede instantiation.

Resolved constraint: the JaCoCo agent's state is global; parallel test execution
races `reset()` across threads. D-13 selects a sequential spike, while D-18
records why exact attribution remains unproven.

**Superseded by D-47 (M2 Faz 0, 2026-08-25):** this whole mechanism -
JUnit listener + JaCoCo `reset()`/`getExecutionData()` - is not the L2
engine. PIT's own coverage-collection phase (`CoverageExporterFactory` +
`LineMapper` SPI) already produces a per-test line map without any reset
choreography of coverdict's own. See `validation/runs/pit-spike/FINDINGS.md`
and §13 below. D-13's sequential-reset design survives only as M2's
documented fallback if a corpus repo cannot get PIT's coverage phase green.

## 3. Scaling measurement

Measured: per-test analysis via one `jacococli` process per exec file, 16 tests
→ 4 959 ms ≈ 309 ms/test, dominated by process startup. Modeled: 5 000 tests
≈ 25 min. The earlier ~60× in-process claim is an unverified upper-bound model,
not a benchmark; D-18 requires measurement before architecture.

**Correction (M2 Faz 0, 2026-08-25):** the 309 ms/test figure is an artifact
of this specific prototype's process-per-test design (`jacococli` shelled
out once per `.exec` file), not a property of per-test coverage generally.
PIT's coverage-collection phase, measured against coverdict's own
`analysis.oracle.*` scope (41 test classes), completed in ~1 second total -
no per-test process spawn, no per-test disk write. The 25-minute,
whole-suite model is also the wrong scope for L2 as redefined by D-47/D-49:
L2 only needs to resolve tests for lines L1 already flagged as changed, not
the whole suite. A real replacement number for larger corpus repos
(assertj/junit-framework/dropwizard) is still open - M2 Faz 2, not measured
yet.

## 4. Redundancy semantics (learned by failing)

First algorithm: "coverage subset ⇒ redundant". On the demo repo it flagged
two well-designed focused tests for deletion because an eager test's coverage
contained theirs, and in an identical-coverage pair it kept the assertion-less
member. D-06 corrected subset direction; D-21 further corrects the claim:
identical execution fingerprints are candidates, not duplicate behavior, and
cannot justify deletion alone. Literature reports high false-positive risk.

## 5. Exclusion layering (learned by failing)

Applying exclusions to detail listings while reading the headline number from
the report's root counter left the headline unchanged by exclusions
(76.1% shown vs 73.4% actual after fix). Basis of D-05: recompute everything
from one filtered dataset.

## 6. License positions

| Component | License | Position |
|---|---|---|
| JaCoCo | EPL-2.0 | binary dependency + agent redistribution is standard practice; NOTICE required |
| pitest | Apache-2.0 | no constraints |
| Descartes | LGPL-3.0 | separate process, never linked (D-09) |
| JavaParser | Apache-2.0 / LGPL dual | use under Apache-2.0 |
| diff-cover | Apache-2.0 | concept reference only; coverdict implements diff-intersection natively |

Target license for coverdict: Apache-2.0. The conservative distribution and
trademark posture is summarized in §8 and D-20; release artifacts still require
an actual dependency/license inventory.

## 7. Competitive scan (Aug 2026, updated with Deep-Dive research)

- SonarQube has broader static test-hygiene rules than first assumed; its
  architectural gap is local diff/runtime evidence, not absence of AST rules.
- JNose Test is the closest open-source detector: Java AST + JaCoCo, but a GPLv3
  research web app without local diff or mutation fusion. Never copy its source.
- Teamscale is the closest overall commercial capability; deep-dive in §7a.
- ArcMutate owns commercial diff-scoped mutation. Open-source PIT removed
  `scmMutationCoverage` and requires caller-supplied targets (D-12).
- Diffblue Cover and Qodo Cover generate tests; they do not provide this audit
  workflow. Pricing and version details are volatile and stay in raw research.

Full evidence and dated URLs: `docs/research-raw/01-competitive-landscape.md`.

### 7a. Teamscale deep-dive

Per-contributor pricing, no public list price: `Licensed Contributors =
max(active 180-day UI/API users, 180-day committers on analyzed paths)` — a
max of two counts, not a union. `teamscale-java-profiler` (open-source JVM
agent, embeds JaCoCo) attributes per-test coverage via explicit
`POST /test/start|end` lifecycle calls rather than automatic detection, and
runs fully offline in `exec-file`/`disk` mode; only the intelligence layer
(Test Gap Analysis, Pareto ranking, diff-coverage) needs the server. TGA's
new-code metric is method-level: `(untested new+changed methods) /
(total new+changed methods) × 100%`, computed server-side only. Pareto test
ranking combines coverage-efficiency, term-similarity, **and LLM-embedding
clustering** — the third heuristic is exactly what AGENTS.md hard rule 1
rules out for coverdict; never an auto-delete suggestion either way. No
mutation engine. Weak-oracle/tautology detection is undocumented (treat as
absent, not confirmed absent). Full evidence:
`docs/research-raw/05-teamscale-deep-dive.md`.

## 8. Release licensing and trademarks

JaCoCo linking/redistribution is compatible with the chosen Apache-2.0 project
license if EPL notices, source availability, and the actual transitive license
inventory are shipped. JavaParser is used under its Apache-2.0 option. Descartes
stays external under the voluntary policy in D-09/D-20. ASF policy is guidance,
not jurisdiction over coverdict. Trademark conclusions remain project policy,
not legal advice: third-party marks stay adjectival next to a descriptive noun
and never become a CLI value, subcommand, package or repo name — which is why
the metric mode is `sonar-compatible`. Full evidence:
`docs/research-raw/02-licensing.md`.

## 9. Per-test coverage under parallel execution

JaCoCo probe arrays are process-global, so overlapping tests cannot be isolated
by reset. Thread-local probes also lose attribution across async/thread-pool
boundaries. A sequential profile is therefore the M2 hypothesis, not proof of
exact attribution: lifecycle, static state, retries, child JVMs, and lingering
async work remain. Earlier 18–22/18–25 minute figures are models. Full evidence:
`docs/research-raw/03-parallel-coverage.md`; decision boundary: D-13/D-18.

**Superseded by D-47 (M2 Faz 0, 2026-08-25):** this entire problem statement
assumed coverdict has to solve JaCoCo probe isolation itself. It does not -
PIT already isolates coverage collection per test inside its own minion
process and exposes a per-test line map through a public SPI. Lifecycle,
static state, and child-JVM risks are still real questions, but they are now
M2 Faz 2's *validation* questions (do PIT's own isolation guarantees hold on
real repos - measured via Jaccard stability, not assumed), not open design
questions coverdict has to solve by writing its own reset choreography. See
§13.

## 10. Known detection limits

- DTO getter/setter test inflation is invisible to coverage + assertion
  analysis (real assertions, unique coverage). Handled by exclusions or by
  L3 mutation, not by L0–L2. Must be documented as a limit, not hidden.
- Static tautology detection is heuristic; the ground truth for "verifies
  nothing" is mutation (L3). L0 is the fast approximation, L3 the slow proof.
- `PSEUDO_TESTED_METHOD` (M5, D-56) approximates Descartes' extreme mutation
  with gregor's `RETURNS`+`VOID_METHOD_CALLS` mutators, the closest
  Apache-2.0 substitute PIT ships (Descartes is LGPL-3.0 and can never be a
  dependency, hard rule 9). Two gaps, not yet measured against real
  Descartes output on any corpus repo: gregor's `RETURNS` mutators replace
  only the return *value*, so side effects before a `return` still execute
  where Descartes would remove the whole body; and no gregor mutator
  approximates extreme mutation for a `void` method at all. See §14.

## 11. Validation repo selection (feeds ROADMAP M0/M1)

Selected for the specific AST/JaCoCo-XML reconciliation problem each forces,
not for popularity: nested-class dollar-sign naming (`Outer$Inner` in JaCoCo
XML vs. child-node AST), dynamic-test source constructs, inheritance, generated
code, and multi-module path resolution. Runtime-event aggregation belongs to
M2, not M1. M0 pins a small canary plus AssertJ Core, JUnit 5, and Dropwizard;
full rationale and alternatives: `docs/research-raw/04-repo-selection.md`.

## 12. Non-Java landscape (context, not a product boundary)

The "no tool fuses coverage + diff-scope + static oracle-quality + mutation +
redundancy" gap holds outside Java too: checked across Python, JS/TS, .NET,
iOS/Swift, and six SaaS platforms (Codecov, Coveralls, DeepSource,
CodeClimate, Qlty, GitHub CodeQL). Stryker (JS and .NET) is the closest
multi-axis tool anywhere — native diff-scoped mutation — but still has no
static oracle-quality check, and its per-test coverage data skips irrelevant
mutants rather than flagging redundant tests. Full evidence:
`docs/research-raw/06-non-java-landscape.md`.

## 13. Per-test attribution engines (M2 Faz 0, 2026-08-25)

A deep-research report (`docs/research-raw/07-jvm-per-test-coverage-research.md`)
surveyed alternative L2 architectures - group testing, ablation, hybrid
static/dynamic call graphs, a custom ASM forked-worker agent - against
existing engines (PIT, JCov, IntelliJ, Azure DevOps TIA, Datadog, Google/Meta
internal systems). Its own evidence undercut its top recommendation (a custom
agent, scored on modeled numbers, beat PIT's measured ones); accepted and
rejected findings are annotated in the archived file itself and summarized in
D-46 through D-50.

The decisive finding came from running the report's own B1 candidate rather
than trusting its description of it: PIT 1.15.8's `exportLineCoverage` output
(`linecoverage.xml`) does **not** match the schema the report described (no
`<line number>`, only PIT's internal block index - see D-49). The real
block-to-source-line resolution path is PIT's own `LineMapper` +
`CoverageExporterFactory` SPI, which is undocumented in the report but
verified working: a ~150-line proof-of-concept
(`validation/runs/pit-spike/exporter-poc/CoverdictLineExporter.java`)
resolved 1996/1996 coverage blocks to correct source lines on coverdict's own
`analysis.oracle.*` package, cross-validated on gson's
`internal.LazilyParsedNumber` (JUnit4 test-name format, same correctness).
Full run logs and the block/line spot-check against real source:
`validation/runs/pit-spike/FINDINGS.md`.

This makes PIT's coverage-collection phase - not a JaCoCo sequential-reset
profile (D-13, now superseded) and not a custom bytecode agent (the report's
recommendation, rejected: it duplicates PIT and breaks D-02) - the L2 engine.
Method-level test-identity resolution and real line attribution are both
solved by consuming PIT's own public extension points. What remains open
(M2 Faz 2, not yet run): whether PIT's own per-test isolation holds stable
under test-order shuffling and parallel execution on larger corpus repos, and
what the coverage-collection phase actually costs at their scale.

## 14. Mutation engine choice for L3 (M5, 2026-08-25)

Nothing in this document discussed pseudo-tested-method detection, extreme
mutation, or PIT's own gregor mutators before M5 - the gap itself was worth
recording, since the idea (approximate Descartes with PIT's built-in
mutators rather than shell out to an LGPL process) was never evaluated or
rejected here, just never proposed.

**Pseudo-tested method**: a method that is *covered* (tests execute it) but
has no mutant a covering test kills - the tests reach the code without
observing what it does. Distinct from an uncovered method (L1's job) and
from a method with no recognized oracle (L0's job): this is the case
neither layer can see, since coverage is real and an assertion may well
exist, just not one that would fail if the method's logic broke.

**Descartes' extreme mutation** replaces an entire method body with a
trivial stand-in (`return null;`, `return true;`, an empty block) rather
than gregor's fine-grained single-instruction mutations (negate a
condition, change one arithmetic operator, alter one return value). This
makes Descartes' mutants a much closer proxy for "does any test verify
this method does anything at all" - which is exactly the pseudo-tested
question - but Descartes is LGPL-3.0 and D-09/D-20 bar it from ever being
a coverdict dependency at any scope, so it was never a candidate to run
even as a user-installed external process for this particular rule (unlike
D-09's original framing, which anticipated Descartes running standalone
for other purposes).

**D-56's substitute**: gregor's `RETURNS` mutator family (`EMPTY_RETURNS`,
`FALSE_RETURNS`, `NULL_RETURNS`, `PRIMITIVE_RETURNS`, `TRUE_RETURNS`) plus
`VOID_METHOD_CALLS`. `RETURNS` mutants change only the returned value,
never the body executed to compute it - a method with a real side effect
before its `return` (a mutable field write, a call to another object) still
performs that side effect under a `RETURNS` mutant, where Descartes'
whole-body replacement would not. `VOID_METHOD_CALLS` only removes void
calls made *from within* the mutated method itself, which is not body
replacement either, and provides no meaningful approximation of extreme
mutation for a method whose own return type is `void`.

**Not done**: no side-by-side run of Descartes and coverdict's gregor
substitute against the same corpus method exists yet, so the practical
precision gap between "extreme mutation" and "RETURNS+VOID_METHOD_CALLS
mutation" is unmeasured, not merely undocumented. `PSEUDO_TESTED_METHOD`'s
confidence tiers (D-56: `HIGH` for pure-`RETURNS` survivors, `MEDIUM` when
`VOID_METHOD_CALLS` is mixed in) are a design choice made from reading
gregor's own mutator source, not from measured precision against a labeled
corpus - ROADMAP's M5 kill criterion (two dogfood repos, manually verified
`PSEUDO_TESTED_METHOD` precision under 90% downgrades or cuts the rule)
is the actual validation this section's judgment still needs.
