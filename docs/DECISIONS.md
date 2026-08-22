# Decisions

Short entries; newest last. Reversals get a new entry, never an edit.

## Decided

**D-01 · CLI first, single executable jar** (2026-08)
Every other surface (IDE plugin, CI bot, AI skill) consumes the CLI's JSON.
No surface work starts before the JSON schema is stable.

**D-02 · Verdict layer over engines, never own engines** (2026-08)
JaCoCo for coverage, git for diffs, PIT/Descartes for mutation. coverdict parses
their outputs and adds interpretation. Re-implementing an engine is out of
scope permanently.

**D-03 · Core in Java 17** (2026-08)
Measured: per-test analysis by shelling out to `jacococli` costs ~309 ms/test
(JVM startup); 5 000 tests ≈ 25 min. In-process use of the JaCoCo Java API
removes the startup cost (~60×). A JVM core also makes the Maven/Gradle plugin
natural. The Python prototype is reference only.

**D-04 · Explicit metric modes with parity requirements** (2026-08, renamed 2026-08)
`jacoco-line` (a line counts if any instruction ran), `strict` (partial lines
don't count), `sonar-compatible` (`(cb + LC) / (cb + mb + EL)`, SonarQube's
blended formula). Verified on the prototype: same data yields 78.9 / 56.3 /
72.8 — so every reported number must name its mode. Parity: `jacoco-line` ==
JaCoCo's own counter, `sonar-compatible` == Sonar UI ±0.1.
Mode name is `sonar-compatible`, never bare `sonar` — SonarSource trademark
policy requires the mark be used only as an adjectival modifier next to a
descriptive noun, never as a standalone parameter value or noun. See
RESEARCH.md §6.

**D-05 · Single exclusion layer** (2026-08)
Exclusions filter the dataset once; all metrics are recomputed from it.
Exclusion globs use SonarQube's `sonar.coverage.exclusions` syntax so teams
maintain one list.

**D-06 · Redundancy semantics** (2026-08)
Identical coverage sets → duplicate cluster; keep the strongest oracle, flag
the rest. Strict subset → the *superset* test is flagged (eager test), never
the subset. Confidence tiers: HIGH only when the removed test's assertions are
a subset of the kept test's. Rationale: the naive subset heuristic flagged
well-designed focused tests in the prototype.

**D-07 · No auto-delete, ever** (2026-08)
Default posture is report. Deletion suggestions require HIGH confidence and
are still suggestions.

**D-08 · v0.1 ships without redundancy detection** (2026-08)
v0.1 = static oracle rules + coverage/new-code/uncovered (L0+L1). Redundancy
(L2) ships only after calibration on real repositories reaches ≥90% precision
on HIGH findings. Shipping the riskiest feature first is the likeliest way to
lose user trust permanently.

**D-09 · Descartes runs as a separate process, never as a declared dependency** (2026-08, tightened 2026-08)
Descartes is LGPL-3.0; coverdict targets Apache-2.0. Confirmed via licensing
research: ASF Category X forbids LGPL in binary releases even at
`<optional>true</optional>` or `<scope>test</scope>` — the prohibition is on
the artifact reaching a binary distribution at all, not on how it's scoped.
Rule: pitest-descartes never appears in `pom.xml`, in any scope. coverdict shells
out to a user-installed `pitest`+`descartes` on PATH and parses its report
files. JaCoCo (EPL-2.0) as a binary dependency is standard ASF Category B
practice — in-process linking is fine, but jacocoagent.jar must be excluded
from the *source* release (Maven POM reference only) and may only appear in
the binary release zip, appropriately labelled. Ship a NOTICE file per the
template in RESEARCH.md §6.

**D-10 · Static analysis uses JavaParser, not regex** (2026-08)
The prototype's regex scanner is demo-grade. Real code (custom assertion
DSLs, parameterized tests, nested classes, Lombok) requires an AST.

**D-12 · Diff-scoped mutation testing is not free from open-source PIT** (2026-08)
Correction to earlier assumption: PIT's `scmMutationCoverage` goal was
deprecated and removed from core pitest — mapping bytecode mutations back to
source-level SCM diffs proved too complex to maintain upstream. Diff-scoped
mutation testing now exists only in ArcMutate (commercial, by the pitest
author). For L3, coverdict must either (a) build its own git-diff → PIT
`targetClasses`/`targetTests` mapping, or (b) treat ArcMutate as an optional
paid integration. Decide which in M5 planning, not before — L3 is two
milestones away.

**D-13 · Per-test coverage: dual-profile sequential analysis run** (2026-08)
Resolves O-03. JaCoCo's probe array is process-global by design (a single
static array per loaded class, written by direct assignment for speed);
thread-local probes were evaluated and rejected as infeasible (JIT
intrinsification loss, memory blow-up, thread-pool context loss). Teamscale's
own testwise agent has the same limitation and explicitly refuses overlapping
tests. Adopted architecture: normal builds keep full parallelism; a separate
Maven/Gradle profile (`forkCount=1`, parallel execution disabled) runs L2
analysis sequentially in one long-lived JVM. Modelled on a 5 000-test suite:
~18–22 min, exact method-level attribution — competitive with process-per-class
isolation (~18–25 min) but with correct attribution instead of class-level
only. L2 is therefore an opt-in "analysis run," not part of every build.

**D-11 · Output contract designed for two readers** (2026-08)
Humans get text/HTML; agents get JSON with stable rule ids
(`NO_ORACLE`, `TAUTOLOGICAL_ORACLE`, `ORACLE_IN_CATCH`, `NULL_CHECK_ONLY`,
`DUPLICATE`, `EAGER_TEST`), file/line locations, confidence, and a one-line
suggested action per finding. An AI-assistant skill is a thin wrapper over
this JSON, not a separate capability.

## Rejected

**R-01 · LLM-as-judge for verdicts** — non-deterministic, costs per run, not
reproducible in CI; also the crowded, undifferentiated corner of the market.
**R-02 · Building on OpenClover for per-test coverage** — source
instrumentation, experimental Java 17 support, no parallel execution.
**R-03 · Python core** — see D-03.

## Open

**O-01 · Project name.** Resolved — **coverdict**. `tqa` was the working name used during research; DECISIONS/RESEARCH history references to `tqa` refer to the same project.
**O-02 · SARIF as an additional output format** — would give GitHub code
scanning and IDE problem-panel integration nearly free. Evaluate in M1.
**O-04 · Maven plugin first or Gradle first** — decide from the maintainer's
real target repositories.
**O-05 · Build our own diff-scoped mutation mapping, or make ArcMutate an
optional integration** — see D-12. Decide at M5.
**O-06 · Metric mode name — `sonar-compatible` vs. a fully neutral term**
(e.g. `blended`, `combined`) for the line+branch formula. Not urgent, decide
by M1. Either way, DECISIONS.md and RESEARCH.md keep stating plainly that
the formula and the local new-code coverage concept are directly inspired
by SonarQube's own "new code" quality gate — the name can be neutral, the
attribution shouldn't be hidden.

~~O-03 · Per-test coverage under parallel execution~~ — resolved, see D-13.
