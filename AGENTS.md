# AGENTS.md

Guidance for AI agents working on this repository.

## What this is

**coverdict** is a test-verdict tool for Java. It consumes evidence
produced by existing engines — JaCoCo coverage data, git diffs, PIT/Descartes
reports — and turns it into verdicts a developer or an AI agent can act on:
which changed lines are untested, which tests lack a recognized oracle, and
which tests are suspiciously coverage-equivalent.

Long-term mission: **coverage says 80%; coverdict says how much of that is
real.** This is the destination, not a v0.1 claim — see D-15 and the
capability boundary in README.md.

coverdict is a verdict layer, not an engine. It never re-implements coverage
measurement or mutation testing.

## Read before working

1. `docs/VISION.md` — who this is for and why existing tools don't answer
2. `docs/DECISIONS.md` — settled decisions. Do not relitigate one silently;
   if you disagree, say so explicitly and propose a new entry.
3. `docs/ROADMAP.md` — the current milestone. Work only inside it.
4. `docs/RESEARCH.md` — measured facts and verified formulas. Cite these
   instead of re-deriving from memory.
5. `docs/GLOSSARY.md` — the Turkish friendly-name mapping for every code
   the HTML report shows (rule ids, `AnalysisReason` codes, enums, mutant
   statuses, metric modes). Add a code here before it can appear unlabeled
   in a report; `ReportLabelsTest` enforces this.

`prototype/` is a validated proof of concept, kept as reference. Production
code is written fresh (see D-03 and D-11 in DECISIONS); do not extend the
prototype's Python analyzer or its mini test harness.

`docs/research-raw/` holds the full deep-research reports RESEARCH.md was
distilled from (~120 KB total). Do not read them by default — RESEARCH.md
carries everything a decision needs. Open one when you need a citation, a
source URL, or the full evidence behind a licensing, competitive or
parallel-coverage position, which now lives only in the raw reports.

## Hard rules

1. **Evidence over judgment.** Every verdict traces to executed code, parsed
   AST, or a diff. Never ask an LLM whether a test is good and store the
   answer as a finding.
2. **Subset is not redundant.** A test whose coverage is a strict subset of
   another test's is never a deletion candidate — the superset test is the
   suspicious one (eager test). Identical probe sets form coverage-equivalent
   candidates, not proof of duplicate behavior; coverage identity alone never
   suggests deletion. This rule was learned by making the opposite mistake;
   see RESEARCH.md §4 and D-21.
2a. **Real code will break a naive parser — assume it from day one.**
   `@Nested` classes, `@ParameterizedTest`/`@TestFactory` (many runtime
   events → one source method), Lombok/Mockito synthetic bytecode with no
   source-line representation, and custom assertion DSLs are not edge cases,
   they are the normal shape of a mature test suite. A regex-based or
   naive-AST scanner will misfire on all four and burn trust on first real
   run — this is why D-10 (JavaParser) is not optional, and why ROADMAP M1's
   validation repos were picked specifically to force each of these
   (RESEARCH.md §11). Treat a false positive here as more costly than a false
   negative: better to miss a bad test than to wrongly flag a good one.
3. **Never auto-delete.** Findings carry confidence (`HIGH`/`MEDIUM`/`LOW`);
   the default posture is report, not remove. Nothing below `HIGH` may even
   suggest deletion.
3a. **Unknown is never green.** Missing, stale, unresolved, unsupported, or
   ambiguous evidence is reported as incomplete. It is never silently dropped
   from a denominator or converted into a coverage or oracle success.
4. **One filtered dataset.** Exclusions apply at a single layer; every metric
   is recomputed from the filtered data. No number is ever read from a raw
   report counter (this desyncs the headline from the details — see
   RESEARCH.md §5).
5. **Metric names are explicit.** Every reported percentage states its
   definition (`jacoco-line`, `strict-line`, `sonar-compatible` — never bare
   `sonar`, see rule 9). Parity requirements: `jacoco-line` must equal
   JaCoCo's own LINE counter exactly; `sonar-compatible` must match the
   SonarQube UI within ±0.1 on the same analysis scope.
6. **Measure before architecting.** Performance claims come from timed runs,
   not intuition. Record the command with the result.
7. **The JSON output contract is the product.** Text, HTML, IDE and skill
   surfaces all render the same JSON. Schema changes are breaking changes and
   need a DECISIONS entry.
8. **Stay inside the milestone.** Ideas beyond it go into ROADMAP.md's backlog
   as one line, not into code.
9. **LGPL never enters a pom.xml, at any scope.** As a conservative project
   distribution policy, coverdict adopts the ASF Category X boundary even
   though it is not an ASF project. Descartes and other LGPL components are
   user-installed external processes, never declared dependencies. Third-party
   trademarks
   (Sonar, JaCoCo, ...) are used only as adjectival modifiers next to a
   descriptive noun in docs, never as standalone CLI parameter values,
   subcommands, package names, or repo names.

## Working agreements

- A new decision, a rejected alternative, or a reversal gets a DECISIONS.md
  entry — 5 lines maximum, dated.
- **Commit as work lands, in small scoped commits with terse messages** —
  one area of change per commit, no batching unrelated edits. Do not wait
  for a whole phase to finish before committing.
- **The repo is the handoff, not the conversation.** Work here regularly
  passes to a different agent/session with no access to this chat. Progress,
  status, and rationale that matters later must live in the repo itself
  (ROADMAP status, a DECISIONS entry, code/commit messages) — never only in
  a chat reply. Update existing docs in place; do not spawn new status files
  per session.
- Any numeric claim in docs must be reproducible: include the command or
  reference the RESEARCH.md section that does.
- Keep docs short enough to load into an AI context without waste. If a doc
  grows past ~150 lines, split or prune it.
- Code, docs, and commit messages are in English. Conversation with the
  maintainer is ALWAYS in Turkish — every chat reply, every status update,
  every summary, with no exceptions and no drifting into English mid-session
  regardless of how long the session runs or what language surrounding text
  (code, docs, tool output) is in. Established software-engineering terms are
  never translated (pipeline, coverage, mutation testing, verdict, oracle...).
- When work is done and verified (tests pass, build green) and the user's
  instruction implied or stated they're OK with committing, actually run the
  commit — do not just report completion and stop short of it. If unsure
  whether a commit was wanted, ask, but do not silently skip it.

## Repository layout

```
AGENTS.md            this file
README.md            what/why in one screen
docs/                VISION, DECISIONS, ROADMAP, RESEARCH
prototype/           validated PoC: demo repo + Python analyzer (reference only)
```
