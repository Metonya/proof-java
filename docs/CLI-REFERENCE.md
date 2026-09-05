# CLI reference — `analyze`

What each flag does, why it exists, and what it changes in the output.
Source of truth for behavior is `AnalyzeCommand.java`, `docs/INPUT-MODEL.md`,
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
| `--config` | Path to `proof.config.json` | auto-detected at repo root |
| `--classpath <id>=<file>` | Jar list (one path per line) for JavaParser symbol solving. Never silently upgrades confidence — missing degrades resolution, present never guarantees HIGH | — |
| `--language-level` | Java language level for JavaParser, 8-21 | `17` |
| `--encoding` | Charset for reading test sources | `UTF-8` |
| `--coverage-exclusions` | Comma-separated `sonar.coverage.exclusions` globs — one filter layer | — |
| `--out` | Verdict JSON output path | `proof-verdict.json` |
| `--html-report` | Optional human-readable HTML report path, rendered from the same verdict document as `--out`/stdout (hard rule 7, D-75) | off |

A repeated id in `--module`/`--source-roots`/`--test-roots` is a rejected
invocation, never a silent last-one-wins.

**Set `--language-level` to the highest level any source file uses, not to the
project's `maven.compiler.release`.** A higher level parses lower-level code
without complaint, so raising it costs nothing; lowering it means any file using
newer syntax cannot be parsed, which surfaces as `UNPARSEABLE_TEST_SOURCE` and an
incomplete run. Measured on google/gson, whose core targets Java 11 but whose
suite contains one record-based test: at level 11 the run was incomplete with one
unparseable file, at level 17 it was complete with an identical finding set. The
default is already 17 - the mistake is lowering it to match the build.

## `fileCoverage` — optional, whole-repo line coverage

| Flag | Purpose | Default |
|---|---|---|
| `--file-coverage` | Emits the `fileCoverage` block: every filtered source file's own line-level `[line, mi, ci, mb, cb]` tuples (same field order as JaCoCo's own `<line>` counters) plus a per-file `MetricSet`, and the `excluded` path list. Off by default — can add several MB on a large report | off |

Built for IDE surfaces (gutter annotations): every metric is computed by the
same `MetricsEngine` `coverage.overall` uses, scoped to one file — an IDE
never recomputes a percentage from raw line tuples itself (hard rule 4).
`excluded` distinguishes an explicitly-excluded file from one that is simply
absent from the report, so a gutter can render "excluded" rather than
"unknown" or "uncovered" (hard rule 3a).

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

One exception to the severity column: a finding on a `@Disabled`/`@Ignore` test
is emitted at `INFO` whatever the rule's own severity is, and its message says
`(disabled test)`. The test is still analyzed and still reported — someone may
re-enable it — but it cannot be the reason a suite is weak today, so it must not
outrank a live oracle-less test in a triage list (D-84).

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
with `--diagnostics-dir`, D-64) rather than a proof-java-side failure. This
is a generic Maven multi-module property, not specific to any one repo -
`doctor` cannot detect it today (see ROADMAP.md backlog).

| Flag | Purpose | Default |
|---|---|---|
| `--per-test-report` | Collects per-test line-coverage evidence via PIT for changed production classes. Never a finding by itself — optional message enrichment for `SUBSUMED_TEST`. Requires a diff mode, unless `--per-test-target` is also given | — |
| `--per-test-classpath <id>=<file>` | PIT's exact test runtime classpath, one entry per line. Distinct from `--classpath` | — |
| `--per-test-target <id>=<FQCN>` | Repeatable: explicit classes to collect L2 evidence for, independent of the diff (`--mutation-target`'s sibling, D-71/Faz 14a). Requires `--per-test-report`; lifts its `--no-vcs` restriction. **All-or-nothing**, same semantics as `--mutation-target`: any target given makes every module's diff-derived L2 targets ignored entirely, including modules with none of their own | — |
| `--per-test-timeout` | Wall-clock budget in seconds for one module's per-test coverage run before it is force-killed. 120s is generous for a diff-scoped target (D-52's measured scale), but a large explicit `--per-test-target` list spanning many classes at once can need more | `120` |

## L3 — mutation evidence (optional, via PIT)

| Flag | Purpose | Default |
|---|---|---|
| `--mutation-report` | Collects mutation evidence (gregor `RETURNS` + `VOID_METHOD_CALLS`). Feeds `PSEUDO_TESTED_METHOD` and `SUBSUMED_TEST`. Requires a diff mode, unless `--mutation-target` is also given | — |
| `--mutation-target <id>=<FQCN>` | Repeatable: explicit classes to mutate, independent of the diff (Plan.md M6 Faz 2 - the IDE's "mutate this class now" gesture). Requires `--mutation-report`; lifts its `--no-vcs` restriction. **All-or-nothing**: giving at least one target makes every module's diff-derived targets ignored entirely, including modules with none of their own. An unresolved class (no matching file under any declared source root) warns as `MUTATION_TARGET_UNRESOLVED` rather than failing the run; a declared module with no `--mutation-target` bound to it warns as `MUTATION_TARGET_NOT_BOUND` | — |
| `--mutation-classpath <id>=<file>` | Same list-file shape, separate opt-in flag | — |
| `--mutation-timeout` | **Idle** timeout in seconds: the module's mutation run is stopped when no class has completed for this long (D-85). Not a total budget — a run that keeps completing classes keeps going. A stopped run still reports the classes it measured, and the run is marked incomplete for the ones it did not | `300` |
| `--diagnostics-dir` | Directory for per-module L2/L3 subprocess logs. Also turns the engine verbose, the only way to see why its own coverage minion died (D-64). Off by default. Each log is capped at 25 MB and says so if it was truncated (D-89) — verbose PIT output reached 184 MB for one module on a real repo | off |

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
  `mutation`/`fileCoverage` blocks when those are enabled.
- **Text** (stdout): the same document rendered for humans, followed by
  `verdict written to <out>`.
- **HTML** (`--html-report`, opt-in, D-75/D-76/D-77/D-79/D-80/D-81): the same
  document rendered as a single self-contained, offline HTML file - a fixed
  sidebar + scroll-spy dashboard, opening in light mode by default.
  [`ReportDataWriter`](../proof-java-cli/src/main/java/dev/proofjava/analysis/report/ReportDataWriter.java)
  turns the document into one presentation-shaped JSON
  object ([`docs/GLOSSARY.md`](GLOSSARY.md) has the friendly-name mapping for
  every rule id/reason code/enum/status it can show), embedded in a
  `<script type="application/json">` that a small static, interpolation-free
  `<script>` reads and renders into the DOM (`textContent`/`setAttribute`
  only, never `innerHTML`/`eval`). Coverage/Mutation/Findings/Files render
  as always-visible dashboard cards (a donut for `jacoco-line`, a mutation
  kill-ratio card with a "Highlighted mutant" spotlight for the most concerning
  mutant, per-rule finding groups with a soft "0" chip for rules that never
  fired, a risk-sorted flat file list with Risk/Package toggle + coverage-band
  filter chips); sections with nothing to show (no changed files, no
  warnings, ...) fold into one "sections left empty in this run" line instead of
  each rendering (or hiding) its own empty card. The report never generates
  an interpretive verdict sentence about the run - only labeled numbers (hard
  rule 1: evidence over judgment; D-81 has the full reasoning). A
  `@media print` rule and `beforeprint`/`afterprint` handlers open every
  collapsible group for printing to PDF. Requires JavaScript; a `<noscript>`
  block says so rather than shipping a silently blank page.

## `render-html` — render an existing verdict JSON, no fresh analysis

```bash
java -jar proof-java-cli/target/proof-java.jar render-html --in proof-verdict.json --out report.html
```

D-78: the read-side counterpart of `analyze --html-report` — takes any file
matching `schema/proof-verdict.schema.json` (`--in`, required) and
renders it with the exact same `HtmlRenderer` (`--out`, required), doing no
evidence collection at all. Built for callers that already have a verdict
document on disk (or composed one from several) and want the HTML without
paying for a re-scan — see D-78 for why `proof-vscode`'s "Export report"
command uses this instead of re-running `analyze`. Exits `2` on a missing or
malformed `--in` file (`VerdictJsonReader` never guesses at a partial
document), `4` if `--out` can't be written, `0` on success.
- Every percentage names its metric mode: `jacoco-line`, `strict-line`,
  `sonar-compatible` — never bare `sonar`. `jacoco-line` matches JaCoCo's own
  LINE counter exactly; `sonar-compatible` matches the same-scope SonarQube
  UI within ±0.1 (verified exact on gson, assertj, junit-framework, and
  dropwizard — `docs/ROADMAP.md`).

## End-to-end examples

**Overall coverage only (L0+L1, no-vcs mode):**
```bash
java -jar proof-java-cli/target/proof-java.jar analyze \
  --no-vcs --report jacoco.xml --out proof-verdict.json
```
Produces `overall` plus L0 findings across every test file. `newCode` is
`unavailable_no_vcs`.

**Changed-code coverage, findings scoped to changed tests (working-tree mode):**
```bash
java -jar proof-java-cli/target/proof-java.jar analyze \
  --uncommitted --report jacoco.xml --findings-scope changed
```
Produces `overall` + a real diff-restricted `newCode`, plus L0 findings only
for test files the diff touched.

**Base-ref mode with mutation evidence (L0+L1+L3):**
```bash
java -jar proof-java-cli/target/proof-java.jar analyze \
  --base main --report jacoco.xml \
  --mutation-report --mutation-classpath root=classpath.txt \
  --mutation-timeout 300
```
Produces all of the above plus a `mutation` evidence block and any
`PSEUDO_TESTED_METHOD`/`SUBSUMED_TEST` findings.

## `doctor` — diagnose a Maven repo before `analyze` runs

```bash
java -jar proof-java.jar doctor --repo .                  # read-only: checks + a suggested command
java -jar proof-java.jar doctor --repo . --fix            # also regenerates broken L2/L3 classpath lists
java -jar proof-java.jar doctor --repo . --write-config   # writes proof.config.json (D-66)
```

`--write-config` writes every usable module's `id`/`root`/`report`/
`perTestClasspath`/`mutationClasspath` to `proof.config.json`'s
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
call - the only place proof-java shells out to a build tool (D-65). Exit `0`
if every module is clean, `3` if any has a BLOCKER.
