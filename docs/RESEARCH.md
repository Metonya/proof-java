# Research notes

Verified facts the project relies on. Each numeric claim was measured on the
prototype (`prototype/demo-bank`, reproduce with `./run.sh`) in Aug 2026
unless noted.

## 1. Coverage metric formulas

Same test run, three definitions, three numbers:

| Mode | Result | Definition |
|---|---|---|
| `jacoco-line` | 78.9% (56/71) | line covered if any instruction ran (JaCoCo LINE counter — parity verified exactly) |
| `strict` | 56.3% (40/71) | partially covered lines don't count |
| `sonar` | 72.8% (91/125) | `(CT + CF + LC) / (2B + EL)`; in JaCoCo terms `(cb + LC) / (cb + mb + EL)` |

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

Open risk: the JaCoCo agent's state is global; parallel test execution races
`reset()` across threads (DECISIONS O-03). OpenClover refuses parallel
execution for the same reason.

## 3. Scaling measurement

Per-test analysis via one `jacococli` process per exec file: 16 tests →
4 959 ms ≈ 309 ms/test, dominated by ~250 ms JVM startup. Extrapolated:
5 000 tests ≈ 25 min. In-process JaCoCo API removes startup cost (~60×).
Basis of D-03.

## 4. Redundancy semantics (learned by failing)

First algorithm: "coverage subset ⇒ redundant". On the demo repo it flagged
two well-designed focused tests for deletion because an eager test's coverage
contained theirs, and in an identical-coverage pair it kept the assertion-less
member. Corrected semantics are D-06. Literature agrees coverage-only
redundancy detection has a high false-positive rate; test-suite-minimization
research (FAST-R, ATM, Nemo) never produced a production Java tool.

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

Target license for coverdict: Apache-2.0. Open item: verify EPL-2.0 agent
redistribution wording and "JaCoCo"/"SonarQube" trademark use in docs
(research prompt R2, see project notes).

## 7. Competitive scan (Aug 2026, updated with Deep-Dive research)

- GitHub `test-quality` topic ≈ 20 repos: Python/TS/Go/Rust, zero Java, most
  0–5 stars, most < 1 year old, majority LLM-judge based.
- SonarQube rules covering test hygiene, confirmed broader than first
  assumed: S2699 (no assertions), S5976 (near-duplicate tests → suggests
  parameterization, syntactic AST match only), S2701/S3415 (assertion
  argument hygiene), S5863 (asserting an object against itself), S5779/S8714
  (assertions swallowed in catch blocks). All static-AST-only: SonarQube
  never executes code, so it cannot see a *dynamically* tautological
  assertion (e.g. a mock configured to return X, then asserted to equal X),
  cannot match two tests with different syntax that hit identical bytecode
  paths, and computes coverage only from externally-supplied JaCoCo XML —
  never live, local, differential. These are architectural blind spots, not
  gaps Sonar is likely to close incrementally.
- **arcmutate** (commercial, by the pitest author, $8–12/user/month, free for
  qualifying OSS): Git-diff-scoped mutation, incremental history, Spring/
  Kotlin support, mutant subsumption analysis, JUnit5 accelerator. Confirmed
  **mutant-centric, not test-centric** — exports `tests.csv` /
  `killing_tests.csv` but does not parse assertion ASTs, does not flag
  tautologies, does not compare per-test coverage vectors for duplicates,
  and does not compute local new-code coverage. Closest commercial neighbour;
  low-to-medium feature overlap despite adjacency. Also confirmed: **PIT's
  own diff-scoped mode (`scmMutationCoverage`) was deprecated and removed
  from open-source pitest** — that capability now lives only in arcmutate
  (see §7a below and D-12).
- **JNose Test** (AriesLab, GPLv3, Maven Central, JDK 25 / Spring Boot 4
  support as of v2.5.0): AST + JaCoCo, 21 test smells including Redundant
  Assertion, Empty Test, Assertion Roulette, Duplicate Assert. The closest
  open-source neighbour on the detection side. Packaged as a Wicket web app
  for repository-evolution research, not a CLI; no git-diff scoping; no
  mutation-testing integration; partial (class/file-level) per-test
  granularity. See VISION.md for how this narrows our gap claim.
  License note: GPLv3 is stronger copyleft than the LGPL we're comfortable
  linking (D-09) — never copy JNose source into an Apache-2.0 codebase.
  Studying its public docs/papers for *which* 21 smells they define and
  *what pattern* each one looks for (algorithms and detection concepts are
  not copyrightable, only the expression is) is fair game and worth doing
  when M1's static-rule set is designed; writing our own JavaParser
  implementation from that understanding is clean-room, copying their Java
  source is not.
- **Teamscale** (CQSE, commercial, €39–115/contributor/month): closest
  capability match overall. Its open-source `teamscale-java-profiler` agent
  gives real per-test ("testwise") coverage and a "Pareto Testing" feature
  that finds the minimal test subset covering everything — i.e. it already
  solves redundancy the way we want to. Gated behind a paid enterprise
  platform; the profiler alone doesn't replace the product.
- Diffblue Cover (proprietary, Java-only, symbolic execution, €10k+/month
  enterprise tier) and Qodo Cover (Apache-2.0, LLM+coverage loop, Java
  support in preview) both **generate** tests to raise coverage; neither
  audits existing or AI-written suites for assertion strength or redundancy.
  Adjacent, not competing.

### 7a. PIT diff-scoping correction

Earlier assumption (superseded): "PIT supports diff-scoped mutation testing
out of the box via `scmMutationCoverage`." Actual state: that goal existed,
proved too complex to keep mapping bytecode mutations to source-level SCM
diffs, and was removed from core pitest. Open-source PIT mutates whatever
`targetClasses`/`targetTests` you configure — it does not compute the diff
itself. Diff-scoping is currently an arcmutate-exclusive capability. See D-12.

## 8a. Licensing — confirmed positions and concrete constraints

Full research: `docs/RESEARCH.md` §6 stands; this adds detail confirmed by
dedicated legal research.

- **JaCoCo (EPL-2.0), Category B**: in-process linking via
  `org.jacoco.core`/`org.jacoco.report` as Maven dependencies is legally
  distinct from a "Modified Work" under EPL-2.0 §1 (separate modules that
  merely link don't trigger copyleft) — confirmed compatible with an
  Apache-2.0 CLI. Binary redistribution of `jacocoagent.jar` inside a release
  zip is permitted under ASF Category B rules, with four obligations: label
  JaCoCo + EPL-2.0 + homepage URL in the README, retain all original
  copyright/trademark notices unmodified, include an EPL-2.0 source
  availability statement, add the NOTICE entry. **Source releases must
  exclude the Category B binary entirely** — reference JaCoCo via `pom.xml`
  only, never commit the jar to the source tree.
- **Descartes (LGPL-3.0), Category X**: forbidden in ASF-style binary
  releases at *any* Maven scope, including `optional`/`test` — the
  prohibition triggers on the artifact reaching a binary distribution, not on
  how it's scoped for compilation. External-process invocation only (D-09).
- **JavaParser**: dual-licensed LGPL/Apache-2.0 by explicit author statement;
  electing Apache-2.0 is a documented one-line NOTICE entry, no LGPL
  obligation attaches.
- **NOTICE file template** (condensed; full text in the licensing report):
  states Apache-2.0 for the CLI core, the JavaParser Apache-2.0 election, the
  JaCoCo EPL-2.0 bundling statement with source-availability pointer, and a
  Descartes line clarifying it is *not* bundled — invocation support for an
  externally, user-installed process only. Includes a trademark-disclaimer
  paragraph: not affiliated with, endorsed by, or sponsored by SonarSource SA
  or the Eclipse Foundation.
- **Trademark usage (SonarSource)**: nominative fair use permits saying
  "reads JaCoCo XML reports" or "Sonar-compatible coverage math" in prose.
  Constraints: Sonar Marks must be adjectival modifiers next to a generic
  noun ("metrics compatible with SonarQube™ platforms" — never a standalone
  noun/verb), never part of a product name, repo handle, or package name
  (avoid `sonar-analyzer`, `com.example.sonar.cli`), and never a bare CLI
  mode value (avoid `--mode=sonar`; use `--format=sonar-xml` or
  `--compatibility=sonarqube`). Basis for renaming the metric mode in D-04.

## 8b. Per-test coverage under parallel execution — resolved (D-13)

JaCoCo writes probes via a single unsynchronized array assignment per
instrumented class (`probes[index] = true`) for speed — no locks, no thread
identity. This is a static field, so every thread executing that class's
methods shares one physical array; there is no theoretical fix that doesn't
sacrifice the performance property that makes JaCoCo usable (thread-local
storage was evaluated and rejected: JIT intrinsification loss, GC pressure
from per-thread probe arrays at enterprise scale, and loss of context across
thread-pool/async boundaries that test methods routinely spawn work into).
Teamscale's own testwise profiler — the most mature open-source attempt at
this — hits the identical wall: it detects overlapping test start/end events
and marks the interrupted test's data invalid rather than attempting
attribution. OpenClover's parallel restriction is confirmed to be an
implementation choice (a global "active recorder pointer"), not a more
fundamental limit than JaCoCo's, and doesn't offer a way around it either.
Quantitative model (5 000 tests / 1 000 classes, 8-core host, 3s app
warm-up): dual-profile sequential run ≈ 18–22 min with full method-level
attribution, versus ≈ 18–25 min for process-per-class isolation with only
class-level attribution and far higher CPU/disk cost. Adopted: D-13.

## 8. Known detection limits

- DTO getter/setter test inflation is invisible to coverage + assertion
  analysis (real assertions, unique coverage). Handled by exclusions or by
  L3 mutation, not by L0–L2. Must be documented as a limit, not hidden.
- Static tautology detection is heuristic; the ground truth for "verifies
  nothing" is mutation (L3). L0 is the fast approximation, L3 the slow proof.

## 9. Validation repo selection (feeds ROADMAP M1)

Selected for the specific AST/JaCoCo-XML reconciliation problem each forces,
not for popularity: nested-class dollar-sign naming (`Outer$Inner` in JaCoCo
XML vs. child-node AST), `@ParameterizedTest`/`@TestFactory` multi-event
aggregation onto one source method, abstract-base-class test inheritance
crediting, Lombok/Mockito synthetic bytecode with no source-line
representation, and multi-module path resolution. Phased order (detail in
ROADMAP.md): AssertJ Core → JUnit 5 → Dropwizard → (fallback) JavaParser,
Apache Commons Collections.
