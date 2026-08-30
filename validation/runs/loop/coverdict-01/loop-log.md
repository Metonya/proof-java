# Loop run 01 — coverdict @ 7ca1075

The first end-to-end run of the canonical workflow in `docs/M0-PERSONA.md`:
an AI agent writes tests, coverdict critiques them, the agent acts, repeat.
Protocol: `skills/coverdict/reference/loop-log-template.md`.

## Setup

| | |
|---|---|
| Repo | coverdict, throwaway clone at `coverdict-ws/coverdict-loop-run` |
| Commit | `7ca1075` (clean tree at clone time) |
| Build | Maven, single module `coverdict-cli` |
| JDK on PATH | Temurin 25.0.4.1 (`maven.compiler.release=17`) |
| Baseline build | `mvn -q verify` — 370 tests, 0 failures, 1 skipped, 18s, green |
| Target class | `dev.coverdict.analysis.subprocess.SubprocessWorkspace` (197 lines, 11 methods) |
| Caps | 3 analyze iterations, 60 min wall clock |

Why a clone: `--uncommitted` must see **only** the loop's new tests. The working
repo carries this session's doc edits, which would pollute the diff. It also
keeps the loop agent structurally separate from the maintainer agent.

### Baseline coverage of the target class

Measured from the clone's own `jacoco.xml` before any loop test was written:

| Counter | Covered | Missed | Total | % |
|---|---|---|---|---|
| LINE | 18 | 28 | 46 | **39.1%** |
| BRANCH | 1 | 7 | 8 | 12.5% |
| METHOD | 8 | 3 | 11 | 72.7% |

Why this class: highest missed-line count among in-process-testable classes.
The 0% classes (`PerTestRunner`, `MutationRunner`, `MutationDriver`,
`PerTestDriver`, `CoverdictLineExporter$LineResolvingExporter`) and the thin
shell-out wrappers (`DoctorCommand` 2.8%, `MavenClient` 12.8%) were **excluded
deliberately**: they are forked-JVM `main()` entry points or Maven subprocess
wrappers, not in-process testable. Listing them as a coverage gap would be
false signal.

---

## Command blocks

**C1 — clone and build the jar**
```bash
git clone coverdict coverdict-loop-run
cd coverdict-loop-run
mvn -q -DskipTests package
```

**C2 — preflight, before any coverage build** (expected to fail; it did)
```bash
java -jar coverdict-cli/target/coverdict.jar doctor --repo .
```
Exit `3`. Verbatim output: `doctor-round0.txt`. One BLOCKER
(`target/site/jacoco/jacoco.xml not found`), two warnings for the absent L2/L3
classpath lists, and — correctly — **no suggested command at all**:
`No module has a usable JaCoCo report yet - nothing to suggest a command for`.
That is hard rule 3a holding in the preflight: it refuses to fabricate an
invocation it cannot justify.

**C3 — baseline coverage build**
```bash
mvn -q verify
```
Exit `0`, 18s, 370 tests / 0 failures / 1 skipped.

**C4 — preflight again**
```bash
java -jar coverdict-cli/target/coverdict.jar doctor --repo .
```
Exit `0`. Report now `found and up to date`; prints a copy-pasteable `analyze`
invocation binding `--module coverdict-cli=coverdict-cli`.

**C5 — generate config and L2/L3 classpath lists**
```bash
java -jar coverdict-cli/target/coverdict.jar doctor --repo . --fix --write-config
```
Exit `0`. Wrote `coverdict.config.json` plus both classpath lists (35 entries
each) via a real `mvn dependency:build-classpath`. After this, `analyze` needs
no `--module`/`--report` flags.

**Round-0 cost:** 3 commands, no friction, no manual fixes. Contrast with the
six WTA dogfood rounds, of which D-65 records that *three of four* lost time to
a setup problem — the `doctor` subcommand those rounds produced is what makes
this round-0 uneventful.

---

## Round 1 — the agent writes tests

Instruction given to the loop agent, verbatim and deliberately free of any
test-quality coaching (mentioning assertions or oracles would contaminate the
experiment):

> the class `SubprocessWorkspace.java` has low test coverage (about 39% of its
> lines). Please add JUnit 5 tests to raise it. [...] make sure the build still
> passes [...] do not modify any file under `src/main/java` — tests only.

**Known contamination risk, recorded rather than hidden:** the loop agent runs
inside the coverdict repo, which carries `AGENTS.md` — a document that discusses
oracles and evidence at length. A real developer in this repo would have it too,
so this is the honest condition rather than a rigged one, but it does mean this
run cannot claim the agent was naive about test quality. A future loop run on a
repo without such a document would be a cleaner measurement.

## Exit-code discipline — provoked deliberately

Run against the same clone while round 1 was in progress (these paths never
touch source files). This is the behaviour `SKILL.md` instructs an agent to rely
on, so it was confirmed rather than assumed.

| Provocation | Exit | JSON written? | Result |
|---|---|---|---|
| `analyze --repo .` (no diff mode) | `2` | **no** | `exactly one diff mode is required: --no-vcs, --uncommitted, or --base <ref>` |
| `analyze --repo . --no-vcs --uncommitted` (two modes) | `2` | **no** | same message |
| `analyze --repo . --base origin/yok-boyle-bir-branch` | `3` | **yes** | `analysis.status: incomplete`, reason `UNRESOLVABLE_REF` |

The exit-3 document carries `coverage.newCode = {"status":"unavailable_incomplete"}`
— the second of the two unavailable statuses, and a real instance of why the
skill must state that `newCode` is sometimes not a number at all. An agent that
coerced that to `0%` would report a false failure; one that ignored it would
report a false pass.

### What the agent produced

8 new `@Test` methods (8 → 16), build green, 370 → 378 tests. It covered
`destroyProcessTree` by spawning a real child JVM through the JDK single-file
source launcher (Mockito is deliberately off this module's test classpath, so
`Process` cannot be mocked), the `UncheckedIOException` arms of `writeLines` and
`writeClasspathArgFile`, and `sanitize` via `createPrivateTempDirectory`.

It also **reported one planned test it could not write** — the
`createPrivateTempDirectory` failure arm, because the JDK rejects a
separator-bearing prefix with `IllegalArgumentException` before any I/O — and
substituted a different test rather than forcing it. Worth recording: this was a
careful agent, not a careless one. A sloppier agent would make coverdict look
better; this run is the harder case.

**Coverage after round 1** (same class, same `jacoco.xml`):

| Counter | Before | After |
|---|---|---|
| LINE | 39.1% (18/46) | **76.1%** (35/46) |
| BRANCH | 12.5% (1/8) | 50.0% (4/8) |
| METHOD | 72.7% (8/11) | 100% (11/11) |

---

## Round 2 — L0+L1 verdict (iteration 1)

**C6**
```bash
java -jar coverdict-cli/target/coverdict.jar analyze --repo . \
  --uncommitted --findings-scope changed --out validation-work/verdict-iter-1.json
```
Exit `0`, 1s, `status: complete`. **0 findings.** → `verdict-iter-1.json`

Two things worth stating plainly:

1. **0 L0 findings is a true negative.** The agent's tests carry real
   `assertThrows`/`assertInstanceOf`/`assertEquals` oracles, so
   `NO_RECOGNIZED_ORACLE` correctly stays silent. The oracle critic had nothing
   to complain about, and did not invent something.
2. **`newCode` is `0/0`, i.e. `percent: null`, in all three metric modes** —
   because the only changed file is a *test* file, classified `excluded` (D-27:
   JaCoCo does not report test code, so its absence is not missing evidence).

Point 2 is a structural property of this workflow, not a defect: **when an agent
only adds tests and changes no production code, the entire L1 half of the tool is
inert by construction.** `M0-PERSONA.md` step 1 says "changes production code
**and/or** writes tests"; in the and/or's second branch, changed-code coverage has
nothing to measure. Worth knowing before anyone builds a CI gate on `newCode`.

Also observed: 8 of the 9 warnings were `UNTRACKED_NON_JAVA_FILE`, all caused by
the loop's own scratch directory (`validation-work/`). Real, minor UX noise — an
agent's own working files inflate the warning list.

---

## Round 3 — L3, the teeth (iterations 2 and 3)

Because no production code changed, there are no diff-derived mutation targets,
so the class is named explicitly. This is exactly what `--mutation-target` is for.

**C7 — under the JDK on PATH (Temurin 25)**
```bash
java -jar coverdict-cli/target/coverdict.jar analyze --repo . --uncommitted \
  --mutation-report \
  --mutation-classpath coverdict-cli=coverdict-cli/target/coverdict-mutation-classpath.txt \
  --mutation-target coverdict-cli=dev.coverdict.analysis.subprocess.SubprocessWorkspace \
  --mutation-timeout 300 --diagnostics-dir validation-work/diag \
  --out validation-work/verdict-iter-2-l3.json
```
Exit `0`, 11s, `status: complete`, **0 findings**, 26 methods with mutants.

And yet: **all 12 mutants across all 10 production methods came back
`NO_COVERAGE`.** The 26 "methods" include 16 from `SubprocessWorkspaceTest` —
PIT mutates test classes too, a known trap.

Root cause, from `diag-jdk25-mutation.log`:

```
MINION : RuntimeException while transforming dev/coverdict/analysis/subprocess/SubprocessWorkspace
MINION : java.lang.IllegalArgumentException: Unsupported class file major version 69
    at org.pitest.reloc.asm.ClassReader.<init>
    at org.pitest.classinfo.ComputeClassWriter.getCommonSuperClass
    at org.pitest.coverage.analysis.CoverageAnalyser.visitEnd
```

The project's own bytecode is **major 61 (Java 17)** — verified directly on both
`SubprocessWorkspace.class` and `SubprocessWorkspaceTest.class`. Major version 69
is **Java 25: the JDK's own classes**. PIT 1.15.8's bundled ASM reads the Java 17
class fine, then fails while resolving a JDK type to compute stack-map frames.

**This is a sharper statement than D-53/D-54.** Those framed the ASM ceiling as a
property of the *target repo's* bytecode. Here the target is Java 17 and the
failure is caused entirely by the **JDK the analysis runs on**.

**C8 — identical command, only the JDK changed (Temurin 17.0.17)**
```bash
"/c/Program Files/Eclipse Adoptium/jdk-17.0.17.10-hotspot/bin/java" -jar ... (identical flags)
  --diagnostics-dir validation-work/diag-jdk17 \
  --out validation-work/verdict-iter-3-l3-jdk17.json
```
Exit `0`, 13s.

| | JDK 25 (C7) | JDK 17 (C8) |
|---|---|---|
| production mutant statuses | 12 × `NO_COVERAGE` (100%) | 7 `KILLED`, 3 `SURVIVED`, 2 `NO_COVERAGE` |
| findings | **0** | **5** |
| `analysis.status` / exit | `complete` / `0` | `complete` / `0` |
| `"Unsupported class file major version 69"` in diag log | 1 occurrence | **0 occurrences** |

Same repo, same tests, same flags, same 26 methods discovered. **Only the JDK
differs.** Under 25 the L3 layer produced nothing usable and said nothing about
it; under 17 it produced five findings.

### The five findings (iteration 3, JDK 17)

| Rule | Conf. | Anchor |
|---|---|---|
| `PSEUDO_TESTED_METHOD` | HIGH | `SubprocessWorkspace#isWindows()Z` |
| `SUBSUMED_TEST` ×4 | MEDIUM | four of the agent's test methods |

---

## Round 4 — acting on the finding (iteration 4)

The finding was handed back to the same agent verbatim, with an explicit escape
hatch: *"If you conclude the finding cannot be acted on without weakening or
faking the test, say so plainly [...] Do not add an assertion that only exists to
kill the mutant."* This is step 5 of the canonical workflow, and the step that had
never been exercised.

**The agent diagnosed it correctly and without help:**

> `destroyProcessTree` calls `process.destroyForcibly()` *unconditionally, after*
> the `if (isWindows())` block. My original test only asserted the immediate child
> died — which is true on both sides of the branch [...] The `taskkill /F /T`
> branch has exactly one observable effect the fallback lacks: it kills
> **descendants**. That is the method's entire documented reason for existing
> (orphaned PIT minion JVMs). I was under-asserting a real behavior, not facing an
> untestable one.

It added one test (`destroyProcessTreeAlsoKillsAGrandchildProcessOnWindows`) that
spawns a child which spawns a grandchild, kills the tree, and asserts via
`ProcessHandle` that the **grandchild** died — and it verified the test was
genuinely mutation-sensitive with a scratch probe before trusting it
(`grandchild alive after destroyForcibly ALONE: true`). It also predicted, in
advance and correctly, that the `isWindows() → true` mutant is an **equivalent
mutant on Windows** that no honest test can kill.

**C9 — re-analyze after the fix**
```bash
mvn -q verify
"<jdk17>/bin/java" -jar coverdict-cli/target/coverdict.jar analyze --repo . \
  --uncommitted --findings-scope changed --mutation-report \
  --mutation-classpath coverdict-cli=... --mutation-target coverdict-cli=...SubprocessWorkspace \
  --mutation-timeout 300 --diagnostics-dir validation-work/diag-iter4 \
  --out validation-work/verdict-iter-4.json
```
Exit `0`, build 18s + analyze 39s. 379 tests green.

| Fingerprint | Rule | Iter 3 → 4 |
|---|---|---|
| `5dde6f48851348ab` | `PSEUDO_TESTED_METHOD` | **resolved** |
| `14e1ce0d0a98ac05` | `SUBSUMED_TEST` | **new** |
| `b7aeccdbb37cdaee`, `d005cdb01cb99c55`, `d9c537fdbe4990dc`, `72c9985c41339a27` | `SUBSUMED_TEST` | carried |

### Two things that must be stated precisely

**1. The finding cleared through the inconclusive path, not through a clean
kill.** `isWindows()`'s mutants went from `{SURVIVED, SURVIVED}` to
`{TIMED_OUT, SURVIVED}`. The `false` mutant now times out — the grandchild
survives, so the test's wait never completes, which *is* real detection. But
`PSEUDO_TESTED_METHOD` "never fires on a method with mutants in a non-final
status", so the rule stopped firing and `MUTATION_INCONCLUSIVE_STATUS` took its
place. An agent that reads only `findings[]` sees "resolved"; the more accurate
reading is "converted into a warning". The convergence signal is weaker than it
looks.

**2. The new `SUBSUMED_TEST` is a true positive caused by the fix.** The agent's
*original* child-only test is now genuinely dominated by the grandchild test,
which asserts strictly more. coverdict names the narrower test, points at the
dominator, and suggests no deletion (hard rule 2). Both tests were kept — the
grandchild test is `@EnabledOnOs(WINDOWS)` by necessity (on POSIX the production
code deliberately does *not* kill the tree), so the older test is the only one
that runs there.

### The single most important number in this run

| | Before round 4 | After round 4 |
|---|---|---|
| `SubprocessWorkspace` LINE coverage | 76.1% (35/46) | **76.1% (35/46)** |
| taskkill/descendant-kill branch | unverified | **verified** |

**Coverage did not move at all.** The load-bearing branch went from "executed but
unobserved" to "observed". A coverage-only quality gate would have scored this
change as exactly zero improvement. That is coverdict's entire thesis, measured
on freshly agent-written code for the first time.

