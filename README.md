# coverdict — test verdict layer for Java

Coverage says 80%. coverdict says how much of that is real.

A verdict layer for Java test suites: it consumes evidence from existing
engines (JaCoCo coverage, git diffs, PIT/Descartes mutation reports) and
produces actionable findings — which changed lines are untested, which tests
assert nothing, which tests duplicate each other. Deterministic, local-first,
no LLM in the loop, output designed to be read by humans and consumed by AI
agents.

**Status:** pre-development. The approach is validated by a working prototype
(`prototype/`); production code has not started. Current milestone: M1
(`docs/ROADMAP.md`).

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
- **License:** Apache-2.0 (D-09/RESEARCH.md §8a for why, and what the NOTICE
  file must contain before the first release)

## Repository map

| Path | What |
|---|---|
| `AGENTS.md` | rules for AI agents working here — read first |
| `docs/VISION.md` | problem, audience, gap analysis |
| `docs/DECISIONS.md` | settled decisions, rejected alternatives, open questions |
| `docs/ROADMAP.md` | current milestone in detail, later ones as sketches, kill criteria |
| `docs/RESEARCH.md` | measured facts: metric formulas, timings, licenses, competitive scan |
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
them and computes coverage in three metric modes. Details in
`prototype/demo-bank/README.md`.
