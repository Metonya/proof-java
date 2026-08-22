# Research notes

Facts, measurements, models, and external observations the project relies on.
Prototype measurements are reproducible with `prototype/demo-bank/run.sh`;
modeled and externally sourced numbers are labeled separately. Floating market
claims are research context, not product acceptance evidence.

## 1. Coverage metric formulas

Same test run, three definitions, three numbers:

| Mode | Result | Definition |
|---|---|---|
| `jacoco-line` | 78.9% (56/71) | line covered if any instruction ran (JaCoCo LINE counter — parity verified exactly) |
| `strict-line+branch` (prototype only; superseded by D-19) | 56.3% (40/71) | `ci > 0 && mi == 0 && mb == 0`; this accidentally added branch completeness |
| `sonar-compatible` | 72.8% (91/125) | `(CT + CF + LC) / (2B + EL)`; in JaCoCo terms `(cb + LC) / (cb + mb + EL)` |

SonarQube's headline "Coverage" blends line and condition coverage. Local
JaCoCo percentages therefore never match Sonar — different formula, not
version drift. Where `cb`/`mb` = covered/missed branches per JaCoCo XML line
attributes, `LC` = lines with `ci > 0`, `EL` = executable lines.

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

## 3. Scaling measurement

Measured: per-test analysis via one `jacococli` process per exec file, 16 tests
→ 4 959 ms ≈ 309 ms/test, dominated by process startup. Modeled: 5 000 tests
≈ 25 min. The earlier ~60× in-process claim is an unverified upper-bound model,
not a benchmark; D-18 requires measurement before architecture.

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
trademark posture is summarized in §8a and D-20; release artifacts still require
an actual dependency/license inventory.

## 7. Competitive scan (Aug 2026, updated with Deep-Dive research)

- SonarQube has broader static test-hygiene rules than first assumed; its
  architectural gap is local diff/runtime evidence, not absence of AST rules.
- JNose Test is the closest open-source detector: Java AST + JaCoCo, but a GPLv3
  research web app without local diff or mutation fusion. Never copy its source.
- Teamscale is the closest overall commercial capability and already provides
  testwise coverage/minimization behind its platform.
- ArcMutate owns commercial diff-scoped mutation. Open-source PIT removed
  `scmMutationCoverage` and requires caller-supplied targets (D-12).
- Diffblue Cover and Qodo Cover generate tests; they do not provide this audit
  workflow. Pricing and version details are volatile and stay in raw research.

Full evidence and dated URLs: `docs/research-raw/01-competitive-landscape.md`.

## 8. Release licensing and trademarks

JaCoCo linking/redistribution is compatible with the chosen Apache-2.0 project
license if EPL notices, source availability, and the actual transitive license
inventory are shipped. JavaParser is used under its Apache-2.0 option. Descartes
stays external under the voluntary policy in D-09/D-20. ASF policy is guidance,
not jurisdiction over coverdict. Trademark conclusions remain project policy,
not legal advice. Full evidence: `docs/research-raw/02-licensing.md`.

## 9. Per-test coverage under parallel execution

JaCoCo probe arrays are process-global, so overlapping tests cannot be isolated
by reset. Thread-local probes also lose attribution across async/thread-pool
boundaries. A sequential profile is therefore the M2 hypothesis, not proof of
exact attribution: lifecycle, static state, retries, child JVMs, and lingering
async work remain. Earlier 18–22/18–25 minute figures are models. Full evidence:
`docs/research-raw/03-parallel-coverage.md`; decision boundary: D-13/D-18.

## 10. Known detection limits

- DTO getter/setter test inflation is invisible to coverage + assertion
  analysis (real assertions, unique coverage). Handled by exclusions or by
  L3 mutation, not by L0–L2. Must be documented as a limit, not hidden.
- Static tautology detection is heuristic; the ground truth for "verifies
  nothing" is mutation (L3). L0 is the fast approximation, L3 the slow proof.

## 11. Validation repo selection (feeds ROADMAP M0/M1)

Selected for the specific AST/JaCoCo-XML reconciliation problem each forces,
not for popularity: nested-class dollar-sign naming (`Outer$Inner` in JaCoCo
XML vs. child-node AST), dynamic-test source constructs, inheritance, generated
code, and multi-module path resolution. Runtime-event aggregation belongs to
M2, not M1. M0 pins a small canary plus AssertJ Core, JUnit 5, and Dropwizard;
full rationale and alternatives: `docs/research-raw/04-repo-selection.md`.
