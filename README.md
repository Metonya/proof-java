# coverdict — test verdict layer for Java

Long-term mission: coverage says 80%; coverdict explains how much confidence
that evidence deserves.

A verdict layer for Java test suites: it consumes evidence from existing
engines (JaCoCo coverage, git diffs, PIT/Descartes mutation reports) and
produces actionable findings — which changed lines are untested, which tests
contain no recognized oracle, and which tests are suspiciously
coverage-equivalent. Deterministic, local-first, no LLM in the loop, output
designed to be read by humans and consumed by AI agents.

**Status:** pre-development. A planted demo validates several mechanisms, not
the product on real repositories. Production code is behind the M0 readiness
gate; the current milestone is M0 (`docs/ROADMAP.md`).

## Capability boundary

| Stage | Honest claim |
|---|---|
| Prototype | Demonstrates formulas, diff intersection, heuristic oracle scans, and per-test probe resets on a mini harness |
| v0.1 / M1 | Changed-code coverage plus conservative static oracle findings; no per-test evidence fusion |
| L2/L3 | Candidate execution equivalence and mutation evidence; only here can the long-term “real coverage” mission be tested |

v0.1 will not claim that a coverage percentage is “real,” that two tests are
behaviorally identical, or that an individual test verifies nothing. Missing or
ambiguous evidence is an incomplete result, never a green result.

## Why

AI-written test suites optimize the visible target — coverage — and routinely
ship assertion-free, tautological, or duplicated tests. The engines that could
expose this (JaCoCo, PIT) produce raw evidence, not verdicts; the server-side
gate (SonarQube) answers too late and only partially. Full argument:
`docs/VISION.md`.

## GitHub setup (once the repo is created)

- **Repository name:** `coverdict`
- **About / short description:** Deterministic verdict layer for Java test
  suites — turns JaCoCo coverage, git diffs, and PIT/Descartes mutation
  evidence into per-test findings.
- **Topics:** `java`, `testing`, `jacoco`, `code-coverage`,
  `mutation-testing`, `test-quality`, `static-analysis`, `developer-tools`
- **License:** Apache-2.0 (D-09/D-20 and RESEARCH.md §8 for the policy; the NOTICE
  file must contain before the first release)

## Repository map

| Path | What |
|---|---|
| `AGENTS.md` | rules for AI agents working here — read first |
| `docs/VISION.md` | problem, audience, gap analysis |
| `docs/DECISIONS.md` | settled decisions, rejected alternatives, open questions |
| `docs/ROADMAP.md` | M0 readiness gate, next milestone, later sketches, kill criteria |
| `docs/RESEARCH.md` | distilled evidence: measurements, models, licenses, competitive scan |
| `docs/research-raw/` | full deep-research source reports (~120 KB) — reference only, not for routine reading; see AGENTS.md |
| `prototype/` | validated proof of concept (reference only, not the foundation) |

## Running the prototype

Requires JDK 17+, Python 3.8+, and a JaCoCo distribution
(`JACOCO_HOME` pointing to a directory containing `lib/jacocoagent.jar` and
`lib/jacococli.jar`):

```bash
cd prototype/demo-bank
JACOCO_HOME=/path/to/jacoco ./run.sh
# reports: build/tqa.html, build/tqa.json, build/jacoco-html/index.html
```

The demo repo contains deliberately planted test smells; the analyzer finds
them and computes coverage in three metric modes. It is feasibility evidence,
not a production validation corpus. Details in `prototype/demo-bank/README.md`.
