---
name: proof-java
description: >-
  Check whether Java tests actually verify anything, using the proof-java CLI.
  Use after writing or repairing JUnit tests in a Java repository; when asked
  if changed lines are covered, if a test asserts anything, if a test is
  redundant or pseudo-tested; or when coverage is high but trust in it is low.
  Runs the build with JaCoCo, invokes proof-java, and acts on the verdict JSON
  in a loop until clean or the human accepts the remainder.
---

# proof-java — verify test quality before committing

proof-java is a **verdict layer**, not an engine. It never measures coverage or
runs mutation testing itself: it reads JaCoCo XML, git diffs and PIT output and
turns them into findings. Every finding traces to parsed source, executed code
or a diff — **findings are evidence, never opinion**.

## The loop

1. Write or repair tests.
2. Build with JaCoCo XML output (`mvn verify` with the JaCoCo plugin bound).
3. Run `proof-java analyze`.
4. Read the verdict JSON.
5. Act on each finding, then go back to step 2.

**Stop when** the verdict is clean, the human accepts the remaining findings, or
you hit the iteration cap (default: 3 analyze runs per class). Report at the cap
instead of grinding.

## Preflight — before the first analyze

1. **Jar present?** `ls proof-java-cli/target/proof-java.jar`, else `mvn -q verify`
   in the proof-java repo. Below, `CJ` means `java -jar <path-to>/proof-java.jar`.
2. **Maven repo? Run `doctor` first and use what it prints:**
   ```
   CJ doctor --repo .
   ```
   It checks source/test roots, compiled output, JaCoCo report presence **and
   freshness** (a report older than the newest `.class` is a BLOCKER), generated
   sources outside `src/main/java`, and L2/L3 classpath validity — then prints a
   copy-pasteable `analyze` invocation. **Use that invocation** rather than
   assembling flags by hand. `doctor` exits `3` when any module has a BLOCKER:
   that is stop-and-fix, not a warning.
3. **Once per repo:** `CJ doctor --repo . --write-config` writes
   `proof.config.json`, after which `analyze` needs no `--module`/`--report`
   flags at all. Add `--fix` to regenerate a broken L2/L3 classpath list.
4. **Not a Maven repo?** `doctor` is Maven-only. Build the binding by hand — see
   `reference/invocations.md`.

## Diff mode — exactly one, or exit 2

| Flag | Use when |
|---|---|
| `--uncommitted` | **Default for this loop** — your new tests are uncommitted |
| `--base <ref>` | Judging a whole branch before push: `merge-base(ref, HEAD)` → working tree |
| `--no-vcs` | No git, or the question is overall coverage only. Forfeits `newCode` entirely and forbids `--findings-scope changed` |

Zero or two of these is rejected before any JSON is written.

## Invocation

```
CJ analyze --repo . --uncommitted --findings-scope changed \
  --out proof-verdict.json
```

Progress goes to **stderr**, prefixed `proof-java: `, with a 30s heartbeat. The
human-readable report goes to stdout. **Parse the JSON file — never the text.**

## Exit codes — read this before anything else

| Exit | Meaning | You must |
|---|---|---|
| `0` | Complete, with or without findings | Proceed to the findings |
| `2` | Invalid invocation — **no JSON is written** | Fix the command; do not look for a verdict file |
| `3` | Incomplete evidence — **JSON IS written** | Read `analysis.incompleteReasons[]`, fix the evidence, rerun. **Never report success.** |
| `4` | Internal error | Stop and surface it to the human |

`1` is reserved and unused in v0.1.

## Reading the JSON

Read these fields and no others:

- `analysis.status` (`complete`/`incomplete`), `analysis.incompleteReasons[].{code,message}`
- `coverage.overall` and `coverage.newCode` — each carries all three metric modes
  (`jacoco-line`, `strict-line`, `sonar-compatible`), each with
  `{numeratorName, numerator, denominatorName, denominator, percent}`.
  `newCode` may instead be `{status: "unavailable_no_vcs"}` or
  `{status: "unavailable_incomplete"}` — neither is a number, and neither is 0%.
- `changedFiles[].{path, classification, newLines, coveredNewLines, uncoveredNewRanges}`
  where `classification` ∈ `mapped | excluded | non-executable | unsupported | unknown`
- `findings[].{rule, severity, confidence, path, startLine, endLine, testMethod,
  productionMethod, message, suggestedAction, fingerprint}`
- `warnings[].{code, message}`

Two rules that matter:

- **Always name the metric mode when quoting a percentage.** Never "coverage is
  78%" — say "jacoco-line 78%". The three modes legitimately differ.
- **`percent` is `null` when `denominator` is 0.** "No executable code" is not 0%.
- **`fingerprint` is the identity across iterations.** It is how you tell "the
  same finding is still there" from "a new finding appeared".

## Acting on findings

| Rule | Proves | Fix by |
|---|---|---|
| `NO_RECOGNIZED_ORACLE` | No recognized assertion anywhere in the test | Add a real assertion |
| `TAUTOLOGICAL_ORACLE` | The assertion cannot depend on the code under test | Assert the real result |
| `CATCH_ORACLE_WITHOUT_FAIL` | A thrown exception skips every oracle and the test still passes | `assertThrows`, or `fail(...)` after the call |
| `NULL_CHECK_ONLY` | Every oracle only checks non-nullness | Assert content, not just presence |
| `PSEUDO_TESTED_METHOD` | Method is covered but every mutant survived | Add an assertion that **observes the method's effect** — never more coverage |
| `SUBSUMED_TEST` | Kill-set is a strict subset of another test's | Examine the **superset** test. **Never a deletion candidate** |

Confidence is `HIGH`/`MEDIUM`/`INCONCLUSIVE` (`LOW` is reserved, unused).
`INCONCLUSIVE` means the detector was uncertain — report it, do not act on it.

Per-rule detail, including the known false-positive shapes, is in
`reference/rules.md`.

## Escalating to L2/L3 — expensive, opt in deliberately

L0+L1 takes seconds. `--mutation-report` runs a real PIT subprocess;
`--mutation-timeout` (default 300s) is an **idle** timeout — a module is only
stopped once no class has completed for that long (D-85), not a total budget.
A run that keeps completing classes keeps going however long it takes.

- **Never on the first pass.** Get L0 clean first.
- Then target one named class: `--mutation-target <id>=<FQCN>` (and
  `--per-test-target` for L2). **All-or-nothing** — giving any target makes every
  module's diff-derived targets ignored entirely.
- The moment a run stalls or fails, add `--diagnostics-dir <dir>`. The engine's
  own minion-crash detail exists nowhere else.

## Never do this

- **Never fabricate** a coverage number, a finding, or a fingerprint. Every claim
  quotes the JSON.
- **Never delete or disable a test** because proof-java flagged it. No rule at any
  confidence suggests deletion — `SUBSUMED_TEST` least of all.
- **Never treat exit 3 as success**, and never treat "no findings" from an
  incomplete run as clean. **Unknown is never green.**
- **Never weaken a test to silence a finding** (dropping an `expected=`, asserting
  a constant, `@Disabled`/`@Ignore`).
- **Never add a suppression without a written `reason` and human sign-off.**
- **Never run `mvn clean` mid-loop** — it deletes the L2/L3 classpath list.
- **Never judge a test by reading it and record that as a finding.** Findings come
  from proof-java only (hard rule 1: evidence over judgment).

## Report what you changed

Say which findings you acted on and which you left alone, and why. A finding you
deliberately ignored is a decision the human needs to see — silently skipping it
looks the same as never having run the tool.
