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

## Repository and diff modes

- `--repo <path>` — repository root. Default: the enclosing git worktree root
  of the current directory; if that discovery fails and `--repo` is absent,
  exit 2.
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
- `--language-level <n>` global, or `<id>=<n>` per module. Default: `17`,
  stated in the output. Parse failures at the configured level classify the
  file `unsupported`, never crash the run (hard rule 2a).
- `--encoding <charset>` — source file encoding, default `UTF-8` (explicitly,
  never the platform default; this machine's Cp1254 is exactly the trap).
- `--classpath <id>=<file>` — optional file listing jars (one path per line)
  for JavaParser symbol solving. Absent or partial classpath degrades oracle
  findings to at most MEDIUM/INCONCLUSIVE (D-17); it never silently upgrades.

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
