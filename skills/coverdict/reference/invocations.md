# Invocation recipes and traps

`CJ` below means `java -jar <path-to>/coverdict.jar`. Flag reference:
`docs/CLI-REFERENCE.md`; input model: `docs/M0-CLI-INPUT.md`.

## Preflight (Maven)

```bash
CJ doctor --repo .                        # read-only: checks + a suggested analyze command
CJ doctor --repo . --write-config         # writes coverdict.config.json (do this once)
CJ doctor --repo . --fix                  # regenerates a broken L2/L3 classpath list
```

Exit `0` = every module clean, `3` = at least one BLOCKER. `--fix` is the only
place coverdict shells out to a build tool.

## L0 + L1 — the default loop pass

Single module, shorthand (module id becomes `root`):
```bash
CJ analyze --repo . --uncommitted --findings-scope changed \
  --report target/site/jacoco/jacoco.xml --out coverdict-verdict.json
```

After `doctor --write-config`, no binding flags are needed at all:
```bash
CJ analyze --repo . --uncommitted --findings-scope changed
```

Multi-module, explicit binding (every flag repeats once per module):
```bash
CJ analyze --repo . --base main \
  --module core=core --source-roots core=core/src/main/java --test-roots core=core/src/test/java \
  --report core=core/target/site/jacoco/jacoco.xml \
  --module api=api  --report api=api/target/site/jacoco/jacoco.xml
```
A **repeated id** in `--module`/`--source-roots`/`--test-roots` is a rejected
invocation (exit 2), never last-one-wins. `--report` is exempt — one module may
legitimately have several reports.

## Useful extras

| Flag | Effect |
|---|---|
| `--file-coverage` | Adds per-file line data. Opt-in; can add several MB on a large report |
| `--coverage-exclusions <glob,...>` | `sonar.coverage.exclusions` syntax, one list for the whole run |
| `--classpath <id>=<file>` | Jar list for symbol solving. Improves resolution; **never** silently upgrades confidence |
| `--language-level <n>` | JavaParser level, default `17`. Global only in v0.1 |
| `--encoding <charset>` | Test-source charset, default `UTF-8` |
| `--config <path>` | Defaults to `coverdict.config.json` at the repo root |
| `--findings-scope all\|changed` | `all` is the default; `changed` requires a diff mode |

## L2 — per-test evidence

```bash
CJ analyze --repo . --uncommitted \
  --per-test-report --per-test-classpath root=target/coverdict-classpath.txt
```

Diff-scoped automatically. To target a class independent of the diff (this also
lifts the `--no-vcs` restriction):
```bash
  --per-test-target root=com.example.Service
```

`--per-test-timeout` is a per-module wall-clock budget in seconds, default `120`
- generous for a diff-scoped target, but a large explicit `--per-test-target`
list spanning many classes at once can need raising.

## L3 — mutation evidence

```bash
CJ analyze --repo . --uncommitted \
  --mutation-report --mutation-classpath root=target/coverdict-classpath.txt \
  --mutation-target root=com.example.Service \
  --mutation-timeout 300 --diagnostics-dir .coverdict-diag
```

`--mutation-timeout` is a per-module wall-clock budget in seconds, default `300`.
`--mutation-target`/`--per-test-target` are **all-or-nothing**: giving at least
one makes every module's diff-derived targets ignored entirely, including modules
that had targets of their own.

## Traps

- **Cross-module test dependencies need `mvn clean install`, not `verify`
  (D-67).** `dependency:build-classpath` always resolves a sibling reactor module
  through its **installed** jar in `~/.m2`, never through its freshly built
  `target/classes`. `verify` never installs, so a stale or missing jar produces a
  real `NoClassDefFoundError` inside PIT's own minion — visible only under
  `--diagnostics-dir`. `doctor` cannot detect this today.
- **`mvn clean` deletes the L2/L3 classpath list file.** The next L2/L3 run then
  fails with `PER_TEST_CLASSPATH_MISSING`. Regenerate with `doctor --fix`.
- **A module stuck at `0/N` in the stderr heartbeat never reached the mutation
  phase at all** (D-64) — a completely different problem from a mutation phase
  that is merely slow. Do not raise the timeout to "fix" it.
- **A stale JaCoCo report silently produces optimistic numbers.** `doctor` treats
  a report older than the newest `.class` as a BLOCKER for exactly this reason.
  Rebuild before analyzing, always.
- **Progress is on stderr, the report is on stdout, the contract is the JSON
  file.** Never scrape stdout.
- **`--no-vcs` forbids `--findings-scope changed`** and both `--per-test-report`
  and `--mutation-report` unless a matching `--target` flag is given.
