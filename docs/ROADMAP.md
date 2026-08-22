# Roadmap

Only the current milestone is specified in detail. Later milestones are
sketches and will be planned when reached.

## M1 — CLI core (current)

One jar: `coverdict analyze`. Scope is L0 + L1 only (D-08).

Deliverables:
- Parse one or more JaCoCo XML reports (multi-module = multiple reports,
  one repo-root git diff)
- Overall coverage in three metric modes (D-04)
- New-code coverage against any git ref; uncovered new-code lines as ranges
- Static oracle rules via JavaParser (D-10): `NO_ORACLE`,
  `TAUTOLOGICAL_ORACLE`, `ORACLE_IN_CATCH`, `NULL_CHECK_ONLY`
- Exclusions: Sonar-syntax globs, single filter layer (D-05)
- Output: JSON (the contract, D-11) + terminal text + standalone HTML

Verification — M1 is done when all of these hold:
1. `jacoco-line` equals JaCoCo's own LINE counter on every tested repo.
2. `sonar` mode matches the SonarQube UI within ±0.1 on an internal repo
   that already runs Sonar, same analysis scope and exclusions.
3. Run cleanly, phased, on repos chosen for the specific parser edge case
   each exposes (full rationale: RESEARCH.md §9):
   - **AssertJ Core** (~9 500 tests) — fluent-DSL multi-line assertions,
     `@Nested` class resolution, single-module, high volume
   - **JUnit 5** itself — `@TestFactory` dynamic tests, Gradle path layout
   - **Dropwizard** — multi-module Maven aggregation, Mockito synthetic
     proxies, mixed JUnit 4/5
   - Fallback if edge cases surface: **JavaParser** (Lombok synthetic
     methods), **Apache Commons Collections** (abstract base class test
     inheritance)
   Zero crashes and a manually reviewed false-positive rate < 10% on static
   oracle findings, on each repo in the phase reached.
4. End-to-end on a repo with ~5 000 tests in under 30 seconds for L0+L1.

## Later (sketches)

- **M2 — Maven/Gradle plugin.** Aggregation for multi-module builds;
  classpath and source roots for free.
- **M3 — CI integration.** PR comment + quality-gate exit codes.
- **M4 — L2 redundancy.** JUnit 5 TestExecutionListener + in-process JaCoCo
  API, run under the dual-profile sequential analysis architecture (D-13) —
  an opt-in profile with parallelism disabled, not part of every build.
  Gated on the precision bar below.
- **M5 — Descartes integration (L3) + IDE surface.** Pseudo-tested methods
  translated into test-centric verdicts. Diff-scoping decision first (O-05,
  D-12): PIT's own SCM-diff mode was removed upstream, so this needs either
  a custom git-diff → PIT target mapping or an optional ArcMutate
  integration. IDE plugin renders the same JSON (gutter marks for uncovered
  lines, problems panel for findings).
- **Backlog:** SARIF output (O-02) · AI-assistant skill wrapping the CLI ·
  non-Java languages (the verdict-layer design is engine-agnostic; each
  language needs its own evidence adapters).

## Kill criteria

Stated up front so scope-cutting is a decision, not a mood:

- If M4 calibration cannot reach **≥90% precision on HIGH-confidence
  redundancy findings** across the maintainer's real repos after two serious
  calibration rounds, L2 is cut from the product and coverdict remains an
  L0+L1(+L3) tool. That tool is still worth shipping.
- If M1's Sonar-parity requirement proves impossible against the internal
  Sonar instance (plugin/version drift), the `sonar` mode is renamed
  `sonar-like` and documented as approximate — trust demands the name not
  overclaim.

## Success criteria that don't depend on GitHub stars

1. Used in anger on the maintainer's own team: AI-generated test PRs get a
   coverdict verdict before review.
2. At least one finding class demonstrably changes behaviour (e.g. weak-oracle
   findings fixed rather than waived).
3. An AI agent consumes the JSON and fixes its own tests without a human
   relaying the findings.
