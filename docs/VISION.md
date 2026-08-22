# Vision

## The problem

AI-assisted development changed who writes unit tests: increasingly, models
do. Model-written suites optimize for the visible target — coverage — and
routinely produce tests that raise the number without raising confidence:

- tests with no assertions, or assertions that hold for any implementation
- several tests exercising the same lines and verifying the same behaviour
- coverage quotas (e.g. "80% on new code") satisfied by construction

The result is a suite that is expensive to run and maintain, and a coverage
number nobody can trust. Teams discover uncovered lines only after CI runs
their quality gate — minutes to hours after the code was written, and after
the AI agent that wrote it has moved on.

## Who this is for

1. **Java teams with AI-generated test bloat and a coverage gate** — they need
   a local, pre-push answer to "is this coverage real, and what did I miss?"
2. **AI coding agents** — an agent that generates tests needs a deterministic,
   machine-readable critic in its loop. LLM self-review does not qualify:
   it is the same failure mode reviewing itself.
3. **Maintainers receiving AI-written PRs** — a fast verdict on whether the
   included tests verify anything.

These are three candidate audiences, not three validated buyers. M0 must name
one primary persona and observe one canonical workflow on three real dogfood
repositories before production development starts. Today, user demand and
setup tolerance are hypotheses; the prototype validates mechanisms only.

## Why existing tools don't answer

Each question below has an engine that produces the *evidence*, and no tool
that produces the *verdict*:

| Question | Engine that has the evidence | Gap |
|---|---|---|
| How much of my *changed* code is tested, computed the way my quality gate computes it? | JaCoCo (coverage) + git (diff) | Neither combines them; SonarQube does, but only server-side, post-push, with its own blended formula |
| Which tests contain no recognized oracle? | Static analysis (partially: SonarQube S2699, PMD) | Framework and helper recognition is incomplete; absence is heuristic, not proof |
| Which methods are executed but not actually verified? | PIT/Descartes (mutation) | Output is mutant-centric ("this mutant survived"); translating it into a per-test verdict needs evidence mutation alone does not give |
| Which tests have suspiciously equivalent execution evidence? | Per-test coverage | Identity is a review candidate, not behavioral proof; subset heuristics target the wrong tests |

The 2025–2026 wave of "test quality" tools (≈20 repos surveyed) is
Python/TypeScript/Go/Rust only — none target Java — and most delegate the
verdict to an LLM, which makes results non-deterministic, non-free, and
non-reproducible in CI.

One Java exception exists and narrows this claim: **JNose Test** (AriesLab,
GPLv3) combines static AST analysis with JaCoCo to detect 21 test smells,
including redundant assertions and duplicate asserts — deterministic,
evidence-based, exactly our philosophy. It is packaged as a Wicket web
application for repository-wide research scans, not a CLI, has no git-diff
awareness, and no mutation-testing integration. The gap we're filling is
specifically: fast local CLI + diff-aware + coverage/mutation fusion, not
"no Java tool exists." See RESEARCH.md §7 for the full competitive scan.

## What coverdict is

A **verdict layer** over existing engines. One CLI, one stable JSON output,
rendered as terminal text, HTML, IDE annotations, or fed to an AI agent.
Findings use a small, fixed vocabulary (rule ids with plain-language names,
confidence tiers, suggested action), in the way SonarQube made "code smell"
and "quality gate" legible to non-experts.

The long-term product fuses these evidence layers. v0.1 does not: it places
aggregate changed-code coverage and conservative static oracle findings in one
contract, but cannot link a weak test to the production lines it covered. Until
L2/L3 are validated, "how much is real" is the mission, not a release claim.

## What coverdict is not

- Not a coverage engine, not a mutation engine, not a test runner.
- Not an LLM judge. No model call ever decides a finding.
- Not an auto-deleter. It reports; humans (or supervised agents) act.
