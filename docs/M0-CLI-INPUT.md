# CLI input model (M0 deliverable 2)

Every input the v0.1 CLI accepts, with its default and failure behavior.
Fail-closed principle throughout (hard rule 3a): an ambiguous or missing input
is a structured error, never a guessed default that could produce false green.

## Invocation

```
java -jar coverdict.jar analyze [options]
```

`analyze` is the only v0.1 subcommand (`--version`/`--help` aside); the
subcommand form leaves room for later additions without breaking flags.
Option precedence: command line > config file > documented defaults.

Config file: `--config <path>`; if omitted, `coverdict.config.json` at the
repo root is used when present. Strict JSON, schema-validated (same schema
discipline as the output, hard rule 7). No comments; entries that need
rationale (suppressions) carry an explicit `reason` field instead.

`modules` (D-66) carries the same binding `--module`/`--report`/
`--per-test-classpath`/`--mutation-classpath` express on the command line -
`coverdict doctor --write-config` generates it. All-or-nothing: a single
`--module` on the command line makes this array invisible entirely, never
partially merged with it.

## Repository and diff modes

- `--repo <path>` — repository root. Default: the current working directory
  (implemented as-is; no git-worktree-root autodiscovery in v0.1 - a repo
  outside the working directory always needs an explicit `--repo`).
- Exactly one diff mode is required; supplying none or several is exit 2.
  No implicit default — an agent must state what "new code" means for the run.
  - `--base <ref>` — **base-ref mode.** Changed lines = diff from
    `merge-base(ref, HEAD)` to the current working tree (committed work since
    the branch point plus uncommitted edits). D-16 semantics: resolved SHAs of
    `ref`, the merge-base, and `HEAD`, plus a dirty flag, are recorded in the
    output.
  - `--uncommitted` — **working-tree mode.** Changed lines = diff from `HEAD`
    to the working tree (staged and unstaged). Same identity recording.
  - `--no-vcs` — **fallback.** No git required or consulted. Overall coverage
    only; every new-code metric in the output is explicitly
    `unavailable_no_vcs`, never computed from a guess (hard rule 3a).
- Unresolvable ref, missing merge-base (shallow history), or git failure:
  exit 3 with a structured incomplete result naming the failing prerequisite.
- `--findings-scope all|changed` — which test sources the oracle critic scans
  (D-28); default `all`. `changed` requires a diff mode (`--base` or
  `--uncommitted`) — rejected under `--no-vcs`, exit 2, since "changed" is
  undefined without a diff. Always recorded in `inputs.findingsScope` so an
  empty `findings` array is never ambiguous between "nothing wrong" and
  "nothing scanned".

## Reports and module binding (D-16)

- `--module <id>=<root-dir>` — declares a module: stable user-chosen id
  (recommended: Maven artifactId) and its root directory, repo-relative.
  Repeatable.
- `--report <id>=<jacoco-xml-path>` — binds a JaCoCo XML report to a declared
  module. Repeatable; a module may have several reports, but overlapping
  class identities between reports are exit 3 (rejected, not counter-merged).
- Single-module shorthand: `--report <jacoco-xml-path>` alone (no `--module`)
  declares one module `root` rooted at `--repo`.
- A report referencing an undeclared module id, or a declared module without
  any report while its sources contain changed Java files: exit 3.
- Freshness: standalone XML cannot prove which commit produced it; the output
  labels such evidence `freshness: unverified` (M1c criterion 3). v0.1 has no
  provenance manifest — that arrives with the M3 build integration.

## Source model

- `--source-roots <id>=<dir>[,<dir>...]` — main source roots per module.
  Default: `<module-root>/src/main/java`.
- `--test-roots <id>=<dir>[,<dir>...]` — test source roots per module.
  Default: `<module-root>/src/test/java`.
- `--language-level <n>` — global only in v0.1 (the documented per-module
  `<id>=<n>` form is not implemented). Default: `17`, stated in the output
  and used by the oracle critic's JavaParser configuration. A test source
  that fails to parse at this level is skipped with a structured
  `UNPARSEABLE_TEST_SOURCE` reason; the run continues (hard rule 2a). For
  the coverage side, `changedFiles[].classification=unsupported` is a
  separate, name-based classification (Kotlin/Scala/etc.), not this parser.
- `--encoding <charset>` — charset used to read test sources for the oracle
  critic, default `UTF-8` (explicitly, never the platform default; this
  machine's Cp1254 is exactly the trap).
- `--classpath <id>=<file>` — optional file listing jars (one path per line)
  for JavaParser symbol solving. Absent or partial classpath degrades oracle
  findings to at most MEDIUM/INCONCLUSIVE (D-17); it never silently upgrades.

## L2 per-test evidence (D-46/D-55)

- `--per-test-report` — collects per-test line coverage for changed
  production classes via an embedded PIT (`org.pitest:pitest*:1.15.8`,
  shaded into `coverdict.jar` itself - never a separate install, D-51).
  Diff-scoped automatically: only classes with changed, mapped source lines
  are targeted. Requires a diff mode; rejected under `--no-vcs`, exit 2
  (same pattern as `--findings-scope changed`). **Runs the module's entire
  test suite once** (PIT owns test discovery; there is no way to narrow it
  to only the tests that might cover the changed lines without already
  knowing the answer) - budget for that cost, not just the diff size. D-46:
  evidence only, `perTest` in the output never adds a `Finding` by itself.
- `--per-test-classpath <id>=<file>` — the exact runtime classpath PIT needs
  to run that module's tests: one entry per line, same list-file shape as
  `--classpath` but a different purpose (that one is an optional JavaParser
  aid; this one is required input PIT cannot run without). Directory entries
  double as PIT's "code under test" paths; a module with changed classes but
  no bound `--per-test-classpath` gets a `PER_TEST_CLASSPATH_MISSING`
  warning, not an error - L2 evidence is always optional (hard rule 3a still
  applies: absent evidence is visible, never silently green).
- A module whose bytecode PIT's bundled ASM cannot read (D-53), whose
  process times out, or whose classpath is otherwise unusable: warned as
  `PER_TEST_COLLECTION_FAILED`, that module's `perTest` entry is simply
  missing - never blocks the rest of the run.
- `perTest` is entirely absent from the output JSON (not present-but-empty)
  unless `--per-test-report` was set.

## L3 mutation evidence (D-46/D-56)

- `--mutation-report` — collects mutant kill-set evidence for changed
  production classes via the same embedded PIT, gregor `RETURNS`+
  `VOID_METHOD_CALLS` mutators (D-56's approximation of Descartes' extreme
  mutation - Descartes stays LGPL and out of process, hard rule 9).
  Diff-scoped the same way as `--per-test-report` (O-05/D-60: reuses the
  same changed-file-to-FQCN-glob mapping). Requires a diff mode; rejected
  under `--no-vcs`, exit 2. `setFullMutationMatrix(true)` - every test that
  kills a mutant, not just the first - needs `numberOfThreads=1`'s
  determinism and costs more than L2's coverage-only pass. D-46: evidence
  only; `mutation` in the output never adds a `Finding` by itself, but
  `PSEUDO_TESTED_METHOD` (a real finding, `docs/rules/PSEUDO_TESTED_METHOD.md`)
  is computed from it automatically whenever the flag is set.
- `--mutation-classpath <id>=<file>` — same list-file shape and purpose as
  `--per-test-classpath`, a separate flag and warning namespace
  (`MUTATION_CLASSPATH_MISSING`) because the two evidence layers are
  independently opt-in.
- `--mutation-timeout <seconds>` — wall-clock budget per module before the
  mutation subprocess (and, on Windows, its entire process tree - D-58)
  is force-killed. Default 300s (5 minutes) - deliberately conservative,
  not generous: D-59 found process count growing unpredictably fast in at
  least one environment for even a small diff-scoped target, root cause
  unresolved. Raise this only after confirming a target environment
  doesn't reproduce that growth.
- A module whose mutation run fails, times out, or has a mutant left in a
  non-final status: warned (`MUTATION_COLLECTION_FAILED`,
  `MUTATION_BUDGET_EXCEEDED`, or `MUTATION_INCONCLUSIVE_STATUS`
  respectively), never blocks the rest of the run - same never-abort
  contract as L2.
- `mutation` is entirely absent from the output JSON unless
  `--mutation-report` was set.

## Exclusions (D-05)

- `--coverage-exclusions <glob[,glob...]>` — `sonar.coverage.exclusions`
  glob syntax, one list for the whole run. Applied once to build the filtered
  dataset; every metric, range, and detail is recomputed from it.

## Metrics and output

- All three metric modes (`jacoco-line`, `strict-line`, `sonar-compatible`)
  are always computed for overall and new-code scopes; there is no mode
  selection flag. Every percentage in every surface carries its mode id and
  raw numerator/denominator.
- `--out <path>` — verdict JSON, default `coverdict-verdict.json` in the
  working directory, byte-deterministic. Human-readable text goes to stdout.
- Exit codes: `0` complete analysis (findings or not) · `2` invalid
  invocation/input · `3` incomplete or unverified-required evidence ·
  `4` internal failure. `1` is reserved for the M3 quality gate.

## Supported / unsupported matrix

| Input | v0.1 behavior |
|---|---|
| Git repo with reachable merge-base | supported (base-ref and working-tree modes) |
| Shallow clone without merge-base | exit 3, structured |
| No `.git` / non-git VCS | `--no-vcs` fallback only, overall coverage only |
| JaCoCo XML (as emitted by 0.8.x, incl. its DOCTYPE line) | supported; DOCTYPE tolerated but never processed (SECURITY-POLICY.md §1) |
| JaCoCo `.exec` binary files | unsupported (exit 2); XML only in v0.1 |
| Merged/overlapping reports (same class in two reports of different modules) | exit 3, rejected |
| Java sources within configured language level | supported |
| Java syntax above configured/parseable level | per-file `unsupported` classification, run continues, result marked accordingly |
| Kotlin/Scala/other JVM sources | out of scope (O-07); their coverage lines are excluded from Java verdicts and listed as unsupported paths |
| Generated sources present in report but absent from source roots | `unknown` classification → exit 3, never silently dropped |
| Windows / macOS / Linux paths, Unicode/spaces/CRLF | supported; normalized repo-relative forward-slash form (D-22, M1c criterion 7) |

Every changed Java path ends in exactly one class: mapped, excluded,
non-executable, unsupported, or unknown (M1c criterion 3).
