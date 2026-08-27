# CLI reference — `analyze`

What each flag does, why it exists, and what it changes in the output.
Source of truth for behavior is `AnalyzeCommand.java`, `docs/M0-CLI-INPUT.md`,
and `docs/rules/`; this page is a navigable summary, not a duplicate spec.

## Evidence levels

| Level | What it proves | Enabled by |
|---|---|---|
| L0 | Static oracle analysis of test source (AST) — does this test assert anything? | Always on |
| L1 | JaCoCo XML + git diff — is changed code covered? | Always on (diff-mode dependent) |
| L2 | Per-test line-coverage fingerprints (redundancy candidates) | `--per-test-report` |
| L3 | Mutation evidence (PIT) — was a line actually exercised meaningfully? | `--mutation-report` |

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Analysis completed — with or without findings (`COMPLETE`) |
| `2` | Invalid invocation/input — no JSON written (`INVALID_INPUT`) |
| `3` | Evidence missing/ambiguous/unverified — never a false green (`INCOMPLETE`) |
| `4` | Internal failure (`INTERNAL_ERROR`) |
| `1` | Unused in v0.1 — reserved for the M3 finding-based quality gate |

## Diff modes — exactly one required

- `--no-vcs` — overall coverage only, no changed-code numbers.
- `--uncommitted` — working-tree mode: `HEAD` → working directory.
- `--base <ref>` — base-ref mode: `merge-base(ref, HEAD)` → working directory.

Zero or more than one of these is rejected with exit `2` before any JSON is written.

## Input / binding flags

| Flag | Purpose | Default |
|---|---|---|
| `--repo` | Repository root | current working directory |
| `--report <id>=<path>` | Binds a JaCoCo XML report to a module. Single-module shorthand: `--report <path>` (id becomes `root`) | — |
| `--module <id>=<root-dir>` | Module definition, repo-relative root | single module `root=.` |
| `--source-roots <id>=<dir>[,<dir>...]` | Module's source root(s) | `<root>/src/main/java` |
| `--test-roots <id>=<dir>[,<dir>...]` | Module's test root(s) | `<root>/src/test/java` |
| `--config` | Path to `coverdict.config.json` | auto-detected at repo root |
| `--classpath <id>=<file>` | Jar list (one path per line) for JavaParser symbol solving. Never silently upgrades confidence — missing degrades resolution, present never guarantees HIGH | — |
| `--language-level` | Java language level for JavaParser | `17` |
| `--encoding` | Charset for reading test sources | `UTF-8` |
| `--coverage-exclusions` | Comma-separated `sonar.coverage.exclusions` globs — one filter layer | — |
| `--out` | Verdict JSON output path | `coverdict-verdict.json` |

A repeated id in `--module`/`--source-roots`/`--test-roots` is a rejected
invocation, never a silent last-one-wins.

## L0 — static oracle critic

Always runs; tuned by:

| Flag | Purpose |
|---|---|
| `--findings-scope all\|changed` | `all` (default): scans every test file under every module's test roots. `changed`: only test files touched by the diff — rejected under `--no-vcs` |
| `customOracles` (in `--config`) | `fully.qualified.Type#methodPattern` (glob `*` allowed) — adds to the oracle allowlist |
| `suppressions` (in `--config`) | `{rule, pathGlob, testMethodPattern?, reason}` — suppresses a finding, `reason` mandatory; suppressed count is visible in the `SUPPRESSED_FINDINGS` warning |

Rules (full firing conditions in `docs/rules/<RULE>.md`):

| Rule | Severity | One-line summary |
|---|---|---|
| [`NO_RECOGNIZED_ORACLE`](rules/NO_RECOGNIZED_ORACLE.md) | WARNING | No recognized assertion/verification call anywhere in the test |
| [`TAUTOLOGICAL_ORACLE`](rules/TAUTOLOGICAL_ORACLE.md) | WARNING | The assertion's outcome cannot depend on the code under test |
| [`CATCH_ORACLE_WITHOUT_FAIL`](rules/CATCH_ORACLE_WITHOUT_FAIL.md) | WARNING | A thrown exception would skip every oracle and the test still passes |
| [`NULL_CHECK_ONLY`](rules/NULL_CHECK_ONLY.md) | INFO | Every oracle only checks non-nullness, never content |

Every finding carries a confidence (`HIGH`/`MEDIUM`/`INCONCLUSIVE`; `LOW` is
reserved but unused in v0.1). No rule at any confidence suggests deletion.

## L2 — per-test evidence (optional, via PIT)

**Cross-module dependency warning (D-67):** if the analyzed module depends
on a sibling reactor module (a real cross-module test dependency, e.g.
`grpc` depending on `service`), build the repo with `mvn clean install`,
**not** `mvn clean verify`, before generating that module's L2/L3
classpath. `dependency:build-classpath` always resolves a sibling module
through its installed jar in the local repository (`~/.m2`), never through
the sibling's freshly-built `target/classes` - `verify` never installs
that jar, so a stale or missing one produces a real
`java.lang.NoClassDefFoundError` inside PIT's own minion (visible only
with `--diagnostics-dir`, D-64) rather than a coverdict-side failure. This
is a generic Maven multi-module property, not specific to any one repo -
`doctor` cannot detect it today (see ROADMAP.md backlog).

| Flag | Purpose |
|---|---|
| `--per-test-report` | Collects per-test line-coverage evidence via PIT for changed production classes. Never a finding by itself — optional message enrichment for `SUBSUMED_TEST`. Requires a diff mode |
| `--per-test-classpath <id>=<file>` | PIT's exact test runtime classpath, one entry per line. Distinct from `--classpath` |

## L3 — mutation evidence (optional, via PIT)

| Flag | Purpose | Default |
|---|---|---|
| `--mutation-report` | Collects mutation evidence (gregor `RETURNS` + `VOID_METHOD_CALLS`) for changed production classes. Feeds `PSEUDO_TESTED_METHOD` and `SUBSUMED_TEST`. Requires a diff mode | — |
| `--mutation-classpath <id>=<file>` | Same list-file shape, separate opt-in flag | — |
| `--mutation-timeout` | Wall-clock budget in seconds before a module's mutation run is force-killed | `300` |
| `--diagnostics-dir <dir>` | Writes each L2/L3 module's whole subprocess log to `<dir>/<module>-<layer>.log` and switches the engine to verbose (D-64) — the only route to the engine's own minion-crash detail. Off by default: verbose output with nowhere to land is just a slower run | — |

L2/L3 runs always print progress to **stderr** as they go (never stdout,
so the text report and JSON stay untouched): a target-class count before a
module starts, a heartbeat with a completed-class counter while it runs,
and a done/failed line at the end. A module still at `0/219` after most of
its budget never reached the mutation phase at all — a different problem
from a mutation phase that is merely slow (D-64).

| Rule | Severity | One-line summary |
|---|---|---|
| [`PSEUDO_TESTED_METHOD`](rules/PSEUDO_TESTED_METHOD.md) | WARNING | A method is covered but every generated mutant survived — nothing observes what it does |
| [`SUBSUMED_TEST`](rules/SUBSUMED_TEST.md) | INFO | A test's kill-set is a strict subset of another test's — it adds nothing under this run's mutators |

`PSEUDO_TESTED_METHOD` anchors on a production method
(`finding.productionMethod`); `SUBSUMED_TEST` names two test methods
(`testMethod`/`relatedTestMethod`). Neither suggests deletion at any confidence.

## Output

- **JSON** (`--out`): schema/tool version, `complete` flag, incomplete
  reasons, `overall` metric set, `newCode` metric set (diff modes only),
  changed-file classification, `findings`, `warnings`, and `perTest`/
  `mutation` blocks when those layers are enabled.
- **Text** (stdout): the same document rendered for humans, followed by
  `verdict written to <out>`.
- Every percentage names its metric mode: `jacoco-line`, `strict-line`,
  `sonar-compatible` — never bare `sonar`. `jacoco-line` matches JaCoCo's own
  LINE counter exactly; `sonar-compatible` matches the same-scope SonarQube
  UI within ±0.1 (verified exact on gson, assertj, junit-framework, and
  dropwizard — `docs/ROADMAP.md`).

## End-to-end examples

**Overall coverage only (L0+L1, no-vcs mode):**
```bash
java -jar coverdict-cli/target/coverdict.jar analyze \
  --no-vcs --report jacoco.xml --out coverdict-verdict.json
```
Produces `overall` plus L0 findings across every test file. `newCode` is
`unavailable_no_vcs`.

**Changed-code coverage, findings scoped to changed tests (working-tree mode):**
```bash
java -jar coverdict-cli/target/coverdict.jar analyze \
  --uncommitted --report jacoco.xml --findings-scope changed
```
Produces `overall` + a real diff-restricted `newCode`, plus L0 findings only
for test files the diff touched.

**Base-ref mode with mutation evidence (L0+L1+L3):**
```bash
java -jar coverdict-cli/target/coverdict.jar analyze \
  --base main --report jacoco.xml \
  --mutation-report --mutation-classpath root=classpath.txt \
  --mutation-timeout 300
```
Produces all of the above plus a `mutation` evidence block and any
`PSEUDO_TESTED_METHOD`/`SUBSUMED_TEST` findings.

## `doctor` — diagnose a Maven repo before `analyze` runs

```bash
java -jar coverdict.jar doctor --repo .                  # read-only: checks + a suggested command
java -jar coverdict.jar doctor --repo . --fix            # also regenerates broken L2/L3 classpath lists
java -jar coverdict.jar doctor --repo . --write-config   # writes coverdict.config.json (D-66)
```

`--write-config` writes every usable module's `id`/`root`/`report`/
`perTestClasspath`/`mutationClasspath` to `coverdict.config.json`'s
`modules` array - after that, `analyze` needs no `--module`/`--report`
flags at all (command-line `--module` still overrides the config's
`modules` entirely if given, never a partial merge).

Walks the Maven reactor and checks, per module: source/test roots, compiled
output, JaCoCo report presence *and freshness* (older than the newest
`.class` is a BLOCKER, not a pass), generated sources outside
`src/main/java`, and L2/L3 classpath list validity (same check
`--mutation-report` applies at collection time - a list with no code path
is a BLOCKER here too, D-64/D-65). Ends with a copy-pasteable `analyze`
invocation built only from modules with no BLOCKER. `--fix` regenerates a
missing/broken classpath list via a real `mvn dependency:build-classpath`
call - the only place coverdict shells out to a build tool (D-65). Exit `0`
if every module is clean, `3` if any has a BLOCKER.
