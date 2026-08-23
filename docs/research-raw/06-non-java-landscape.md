# Non-Java test-verdict tooling landscape (Aug 2026)

Sourced by an external research pass, reviewed and lightly corrected by the
coverdict maintainers before filing. Distilled into RESEARCH.md §12; read
this file only for full citations or per-tool detail. Scope: Python,
JavaScript/TypeScript, .NET, iOS/Swift, plus six multi-language SaaS
platforms (Codecov, Coveralls, DeepSource, CodeClimate, Qlty, GitHub Code
Scanning/CodeQL).

## Review notes (coverdict team)

- **Correction to the informal HTML comparison shared earlier in chat**:
  Stryker Mutator (both StrykerJS and Stryker.NET) does have native
  diff-scoped execution (`--since`/`--diff` against a branch), which the
  earlier draft had marked absent/partial. Fix before treating that HTML
  table as current.
- The report's own summary matrix filled a "per-test / redundancy" column
  for both Stryker variants with prose about *mutant-run execution
  architecture* (worker-thread isolation for JS, `coverage-analysis=perTest`
  mutant-skipping for .NET) rather than actual duplicate-test detection.
  Neither Stryker flags two tests as behaviorally redundant; per-test
  coverage there exists only to skip mutants a test can't reach, which
  speeds up a mutation run but is not Axis E as we define it. The overall
  "no ecosystem does static-oracle + mutation + redundancy fusion" claim
  survives this correction (Axis C is absent everywhere regardless), but the
  Stryker cells specifically need re-marking to `Has` (diff-scope) / `Absent`
  (redundancy) rather than mixed.
- The "StrykerJS is more mature than Java's PIT ecosystem" claim is backed
  partly by a blog aggregator source (`qaskills.sh`) rather than primary
  adoption data (download counts, contributor counts, release cadence). The
  qualitative conclusion is plausible but is weaker evidence than this
  project's own "measure before architecting" standard (AGENTS.md rule 6)
  would accept for a claim in RESEARCH.md — keep it as an impression, not a
  verified fact, unless re-sourced.
- A couple of footnote citations for cosmic-ray (Python) look mismatched —
  one points to an unrelated pytest plugin (`pytest-gremlins`), another to a
  Docker wrapper repo for a *different* tool. cosmic-ray's own claims here
  were already flagged `(Estimated)` in the original report; treat them as
  low-confidence pending a direct look at `github.com/sixty-north/cosmic-ray`.

## Capability axes

Same six axes used for the Java scan (RESEARCH.md §7) and the HTML
comparison: (A) coverage measurement, (B) diff-scoped/new-code coverage,
(C) static oracle-quality checking, (D) mutation testing, (E) per-test
redundancy/duplicate detection, (F) single-tool verdict fusion of 2+ axes.

## Python

- **coverage.py** (Apache-2.0) — line/branch coverage, per-test dynamic
  contexts. Axis A only.
- **diff-cover** (Apache-2.0) — wraps coverage.py output against a git diff;
  closest existing tool anywhere to coverdict's own L1. Axis A+B, no C/D/E.
- **mutmut** (BSD-3) — mutation testing via LibCST, restricts mutations to
  covered lines using coverage.py, filters invalid mutants with a static
  type checker. Axis D only, not diff-scoped.
- **cosmic-ray** — mutation testing via isolated subprocesses/Celery
  workers. Axis D only. License/recency **estimated**, not confirmed — see
  review note above.
- Static test linters (flake8-pytest-style, Ruff pytest rules) — syntactic
  assertion-presence checks only, no tautology/semantic oracle detection.

## JavaScript / TypeScript

- **c8 / nyc** (ISC) — coverage via V8's native engine hooks. Axis A only.
- **StrykerJS** (Apache-2.0) — mutation testing across Jest/Vitest/Mocha via
  worker-thread isolation; native `--since` branch diffing. Axis B+D. No C,
  no real E (see review note).
- **eslint-plugin-jest** (MIT) — `jest/expect-expect` and similar rules
  check for assertion-call *presence*, not tautology or semantic strength.
  Axis C, syntactic only.

## .NET

- **coverlet** (MIT) — cross-platform coverage via MSBuild task injection.
  Axis A only.
- **Stryker.NET** (Apache-2.0/MIT) — Roslyn-based mutation testing;
  `--since:main` filters mutation generation to changed AST nodes,
  `coverage-analysis=perTest` maps tests to mutated lines to skip
  irrelevant runs. Axis B+D, partial per-test data (not Axis E as defined —
  see review note). No C: it treats any test passing against unmutated code
  as a valid oracle, explicitly does not check assertion quality.

## iOS / Swift

- **xcov / native xccov** (MIT) — parses Xcode's binary coverage format.
  Axis A only; low release velocity / legacy maintenance status.
- **Muter** (MIT) — SwiftSyntax-based mutation testing; narrow operator set
  (relational/logical replacement, statement-block removal only, missing
  method-call removal or return-value mutation), full `xcodebuild`
  recompilation per mutant (no in-memory patching), no diff-scoped
  filtering. Axis D only, least mature of the four ecosystems' mutation
  tools evaluated.

## Multi-language SaaS platforms

| Platform | A | B | C | D | E | F |
|---|---|---|---|---|---|---|
| Codecov | Yes | Yes (PR gating) | No | No | Partial (impact analysis) | No |
| Coveralls | Yes | Yes | No | No | No | No |
| DeepSource | Yes | Yes | Syntactic AST linters | No | No | No |
| CodeClimate | Yes | Yes | Syntactic AST linters | No | No | No |
| Qlty | Yes | Yes | Syntactic AST linters | No | No | No |
| GitHub CodeQL | No | No | Semantic AST rules (security-flaw focused, not test-oracle focused) | No | No | No |

None of the six fuses diff-coverage with static oracle-quality or mutation
signal into one score. DeepSource/CodeClimate/Qlty catch missing-assertion
smells the same syntactic way ESLint/Ruff do, layered onto a coverage
dashboard rather than fused with it.

## Positioning conclusion

The "no tool fuses coverage + diff-scope + static oracle-quality + mutation
+ redundancy into one verdict" claim, drafted for Java in VISION.md/
RESEARCH.md §7, holds when broadened to Python, JS/TS, .NET, iOS/Swift, and
the six SaaS platforms checked here — **with the Stryker correction above
factored in**. Even Stryker, the closest multi-axis tool found in any
ecosystem, never touches static oracle-quality (Axis C), which is exactly
the axis coverdict's v0.1 static critic (M1b) targets first.

## Cited sources (as delivered, not independently re-verified)

https://coverage.readthedocs.io/ ·
https://pypi.org/project/diff-cover/ ·
https://pypi.org/project/mutmut/ ·
https://github.com/sixty-north/cosmic-ray ·
https://www.npmjs.com/package/c8 ·
https://stryker-mutator.io/ ·
https://stryker-mutator.io/docs/stryker-net/configuration/ ·
https://stryker-mutator.io/docs/stryker-net/migration-guide/ ·
https://www.npmjs.com/package/eslint-plugin-jest ·
https://github.com/coverlet-coverage/coverlet ·
https://github.com/fastlane-community/xcov ·
https://github.com/muter-mutation-testing/muter ·
https://qaskills.sh/blog/mutation-testing-stryker-guide-2026 (secondary
source, see review note)
