# proof-java

A deterministic CLI that reads the evidence your build already produces
(JaCoCo coverage, the git diff, PIT mutation results) and reports which of
your Java tests actually verify something.

**Status: v0.1.1, early development.** It runs, it is tested, and it has
been pointed at real repositories. Precision numbers per rule are still
being measured (`docs/VALIDATION.md`).

## The problem

Coverage tells you a line executed. It does not tell you that anything checked
what the line did. A test with no assertion, a test that asserts a constant
equals the same constant, or a test whose every check sits inside a `try` block
that swallows the exception: all three run green and all three raise coverage.

This gets worse when tests are generated rather than written, because coverage is
the visible target and an assertion-free test hits it perfectly. The engines that
could expose this, JaCoCo and PIT, produce raw evidence, not verdicts, and the
server-side gate answers after the merge.

proof-java is the layer in between: it consumes that evidence locally and turns
it into specific, actionable findings.

## What it finds

| Rule | Reads as | What it means |
|---|---|---|
| `NO_RECOGNIZED_ORACLE` | Test with no assertion | No assertion, verification or expected exception anywhere in the test. |
| `TAUTOLOGICAL_ORACLE` | Self-proving test | The assertion cannot depend on the code under test: constant vs. constant, a value vs. itself, a literal boolean. |
| `CATCH_ORACLE_WITHOUT_FAIL` | catch block without fail() | Every assertion sits inside the `try`; if the code throws, they are all skipped and the test still passes. |
| `NULL_CHECK_ONLY` | Null check only | Every assertion checks non-nullness and nothing else. Advisory, not a defect. |
| `PSEUDO_TESTED_METHOD` | Pseudo-tested method | Tests execute this method, but every mutant of it survived: nothing observes what it does. |
| `SUBSUMED_TEST` | Subsumed test | This test's killed-mutant set is contained in another's. The broader test is the suspicious one, not this one. |

Full firing conditions, including what each rule deliberately does *not* flag,
are one file per rule in [`docs/rules/`](docs/rules/).

## Quick start

Requires a JDK (see [Requirements](#requirements)) and a JaCoCo XML report from
your build.

```bash
mvn verify                      # produces target/site/jacoco/jacoco.xml
java -jar proof-java.jar analyze --repo . --no-vcs \
  --report target/site/jacoco/jacoco.xml
```

Real output, from the deliberately-flawed fixture project in this repo:

```
proof-java: analysis complete (no-vcs)
  jacoco-line        91.7% (11/12)
  strict-line        75.0% (9/12)
  sonar-compatible   80.0% (16/20)
  new code: unavailable_no_vcs
7 finding(s) (all):
  CATCH_ORACLE_WITHOUT_FAIL HIGH .../CalculatorCatchWithoutFailTest.java:12 An exception thrown in the try block is caught and swallowed without any assertion.
  NO_RECOGNIZED_ORACLE HIGH .../CalculatorNoOracleTest.java:15 Test 'subtractHasNoAssertion' contains no recognized assertion, verification, or expected exception.
  TAUTOLOGICAL_ORACLE HIGH .../CalculatorTautologicalOracleTest.java:16 Both operands are compile-time constants; this assertion holds for any implementation.
```

Three coverage numbers, never one: the same run measured three defensible ways,
each shown with its own numerator and denominator. A percentage without them
hides how much it rests on.

To scope the analysis to what you actually changed, the fast, everyday mode,
replace `--no-vcs` with a diff mode:

```bash
java -jar proof-java.jar analyze --repo . --base main \
  --report target/site/jacoco/jacoco.xml \
  --html-report proof-report.html
```

### Installing

Grab the latest jar from [Releases](https://github.com/Metonya/proof-java/releases/latest),
or build it from source:

```bash
mvn -q verify
# produces proof-java-cli/target/proof-java.jar
```

Each release is a single executable jar attached to a GitHub Release, with
`NOTICE`, `LICENSE`, `THIRD-PARTY.txt`, and `SHA-256SUMS` alongside it
([`docs/RELEASE-CONTRACT.md`](docs/RELEASE-CONTRACT.md)). **One jar is the
whole distribution on every platform**: nothing to install beyond a JDK, and
no plugin to add to your build.

### Optional: run it as `proof-java` instead of `java -jar ...`

The jar is the actual distribution; there's no separate installer. If you'd
rather type `proof-java analyze ...` than `java -jar
/path/to/proof-java.jar analyze ...`, put the jar somewhere stable and point
your own shell at it: a one-time, local setup, the same idea on every OS,
saving a fixed path to the jar as a shell alias/function. A common convention
is `~/.proof-java/proof-java.jar` (`%USERPROFILE%\.proof-java\proof-java.jar`
on Windows), but any path you like works.

**macOS / Linux**: add to `~/.bashrc` or `~/.zshrc`:

```bash
alias proof-java='java -jar "$HOME/.proof-java/proof-java.jar"'
```

**Windows (PowerShell)**: add to your profile (`notepad $PROFILE`):

```powershell
function proof-java { java -jar "$HOME\.proof-java\proof-java.jar" @args }
```

Reload your shell (or open a new terminal) and `proof-java analyze ...` runs
the jar directly.

## How it works

Four evidence levels, each proving more and costing more. L0 and L1 always run;
L2 and L3 are opt-in.

| Level | Evidence | What it can prove | Enabled by |
|---|---|---|---|
| **L0** | Your test sources, parsed to an AST | This test contains no assertion at all | always |
| **L1** | JaCoCo XML + the git diff | These changed lines are not covered | always |
| **L2** | Per-test line coverage, collected via PIT | These two tests execute the same lines | `--per-test-report` |
| **L3** | Mutation results from PIT | Nothing observes what this method does | `--mutation-report` |

**Diff-scoping is what keeps it cheap.** Given `--base main` or `--uncommitted`,
the expensive levels only look at production code the diff actually touched,
instead of mutating your whole codebase. That is the difference between a check
you run per commit and one you run overnight. `--no-vcs` turns diff-scoping off
and reports whole-repository coverage only.

proof-java never runs your tests itself and never re-implements coverage or
mutation. It reads what JaCoCo and PIT produce, and decides what it means.

## Requirements

Two different things constrain versions here, and conflating them would be
wrong: the JDK **proof-java runs on**, and the Java **language level of the code
it reads**.

### The JDK that runs proof-java

| JDK | L0 / L1 | L2 / L3 | Status |
|---|---|---|---|
| 8–16 | not supported | not supported | the jar is compiled for 17 |
| 17 | yes | yes | **verified** |
| 18–22 | yes | expected to work | untested |
| 23–24 | yes | **not supported** | inferred from the engine's limit, untested |
| 25+ | yes | **not supported** | **verified broken** |

Why L2/L3 stop: the embedded mutation engine (PIT 1.15.8) bundles a bytecode
reader that understands class files up to Java 22. On a newer JDK it cannot read
the JDK's own classes. It does not fail loudly when this happens: every mutant
comes back "never executed", so the run would report zero findings and look
clean. proof-java refuses to collect that evidence instead, and says why (exit
code 3). **Run L2/L3 on a Java 17 JDK.**

### The code being analysed

`--language-level` accepts **8 to 21**, the range the embedded parser supports;
anything else is rejected rather than silently parsed at a lower level.

Set it to the highest level any source file uses, not to the project's
`maven.compiler.release`. A higher level parses older code fine, so raising it
costs nothing, while lowering it makes newer files unparseable and the run
incomplete. The default of 17 is usually right as-is.

Your JaCoCo, not proof-java, decides which class-file versions can be measured.

### Build tools

`analyze` itself reads a JaCoCo XML report, a git diff, and (for L2/L3) a
plain-text classpath file. None of that is Maven-specific, and neither is
the convenience layer anymore: `doctor` auto-discovers plain-Java Gradle
modules from `settings.gradle(.kts)` the same way it walks a Maven
reactor's `<modules>`, and `doctor --fix` generates their L2/L3 classpath
lists too, via a `--init-script` run through the repo's own Gradle Wrapper
(never touches `build.gradle(.kts)`) instead of `mvn
dependency:build-classpath`.

| Build tool | L0/L1 (coverage, oracle findings) | `doctor` auto-discovery | L2/L3 (per-test, mutation) |
|---|---|---|---|
| Maven | yes | yes | yes |
| Gradle (plain Java) | yes | yes (requires a committed Gradle Wrapper) | yes (requires a committed Gradle Wrapper) |
| Gradle/Android (AGP) | yes for unit tests, wired by hand | not yet | **not supported** (PIT has no AGP support) |

`doctor` only ever invokes a repo's own committed `gradlew`/`gradlew.bat`,
never a bare `gradle` on PATH - a repo without a wrapper committed needs
`analyze` wired by hand instead:

```bash
java -jar proof-java.jar analyze --repo . --no-vcs \
  --report build/reports/jacoco/test/jacocoTestReport.xml \
  --source-roots src/main/java --test-roots src/test/java
```

Gradle's `jacocoTestReport` task does not write XML by default; add
`reports { xml.required.set(true) }` to it first. `doctor` prints this same
guidance when it finds a Gradle marker file but cannot resolve a module
from it (no `include(...)` subprojects, no sources under the root).

Module discovery reads `settings.gradle(.kts)`'s `include(...)` calls,
including a same-purpose wrapper function (e.g. `includeProject(name)`) -
not only a bare `include(...)`. `includeBuild(...)` (composite builds, a
separate Gradle root) is never treated as a subproject; `includeFlat(...)`
(sibling, not subdirectory, project roots) is not specially handled and is
a known gap. Verified against a real, complex multi-module project
([junit-framework](https://github.com/junit-team/junit-framework), Gradle
9.7.1, Isolated Projects + Configuration Cache both enabled): `doctor`
finds all 22 real modules, and `--fix` generates a valid classpath for a
mixed Kotlin+Java module, with the target repo's own build files
untouched throughout.

Two honest gaps, not yet worked around: Kotlin test sources are invisible to
the L0 assertion analysis (it parses Java; JaCoCo still measures Kotlin
coverage correctly, so L1 numbers are unaffected), and PIT's mutation engine
has no supported integration with the Android Gradle Plugin, so L3 is not
available for Android modules regardless of how the classpath is supplied.

## Configuration

Every flag also has a config-file equivalent in `proof.config.json` at your repo
root; the command line wins over the file, which wins over the defaults. The
flags most runs need:

| Flag | Purpose |
|---|---|
| `--base <ref>` / `--uncommitted` / `--no-vcs` | Exactly one required: what counts as "changed" |
| `--report <path>` | The JaCoCo XML. Repeatable as `<module-id>=<path>` for multi-module builds |
| `--out` | Verdict JSON path (default `proof-verdict.json`) |
| `--html-report <path>` | Also write a self-contained, offline HTML report |
| `--findings-scope changed` | Only scan test files the diff touched |

Full reference, including L2/L3 setup and every exit code:
[`docs/CLI-REFERENCE.md`](docs/CLI-REFERENCE.md). Run `proof-java doctor` to have
it inspect a Maven repository and print the invocation it thinks you want.

## Reading the output

The JSON is the contract; the terminal text and the HTML report are two
renderers of the same document.

```json
{
  "rule": "NO_RECOGNIZED_ORACLE",
  "severity": "WARNING",
  "confidence": "HIGH",
  "path": "src/test/java/com/example/CalculatorTest.java",
  "startLine": 15,
  "testMethod": "com.example.CalculatorTest#subtractHasNoAssertion()",
  "message": "Test 'subtractHasNoAssertion' contains no recognized assertion, verification, or expected exception.",
  "suggestedAction": "Add an assertion on the observed behavior, or register the helper as a custom oracle in configuration.",
  "fingerprint": "5078b667525bf0b7"
}
```

Exit codes are the part to wire into a script:

| Code | Meaning |
|---|---|
| `0` | The analysis completed, with or without findings |
| `2` | The invocation was invalid. No JSON is written |
| `3` | Evidence was missing, ambiguous or unusable. **Never a false green** |
| `4` | Internal failure |

Findings do not fail the run. Exit `1` is reserved for a future opt-in quality
gate and is deliberately unused today.

Unfamiliar terms (oracle, mutant, subsumed, pseudo-tested, ambient line) and
every warning code the report can show are defined in
[`docs/GLOSSARY.md`](docs/GLOSSARY.md).

## What it does not claim

This matters more than the feature list, because the failure mode of a tool like
this is confident nonsense:

- **It never says a coverage percentage is "real".** It reports three
  definitions with their inputs, and leaves the judgment to you.
- **It never recommends deleting a test.** When two tests overlap, the broader
  one is the suspicious one, and even then the finding is informational.
- **It never asks a model whether a test is good.** Every finding traces to a
  parsed AST, a diff, or an executed mutant. There is no LLM in the loop.
- **Missing evidence is reported, never absorbed.** An unparseable test file, a
  module with no report, an unusable JDK: each produces a named reason and an
  incomplete result, never a quietly cleaner number.

## For AI coding agents

[`skills/proof-java/`](skills/proof-java/) is a skill for agents that write
tests: it runs the analysis, reads the verdict JSON, and acts on the findings in
a loop. It renders proof-java's output and never authors a finding of its own.

Install it without cloning the repo:

```bash
mkdir -p ~/.claude/skills/proof-java && curl -sL https://github.com/Metonya/proof-java/archive/refs/heads/main.tar.gz | tar -xz --strip-components=3 -C ~/.claude/skills/proof-java "proof-java-main/skills/proof-java"
```

Swap the destination for your tool's own skills directory: `.windsurf/skills/proof-java`
for Windsurf, `~/.gemini/antigravity-cli/skills/proof-java` for Antigravity,
or `.agents/skills/proof-java` (repo root) for the portable convention Cursor,
OpenAI Codex CLI, Gemini CLI, and GitHub Copilot all read directly.

Using the [VS Code extension](https://github.com/Metonya/proof-vscode)? Run
**Proof: Install Skill for AI Agent** instead. Same content, and it picks the
right location and scope for you.

## Documentation

| Path | What |
|---|---|
| [`docs/VISION.md`](docs/VISION.md) | Who this is for, and why existing tools don't answer |
| [`docs/CLI-REFERENCE.md`](docs/CLI-REFERENCE.md) | Every flag, what it changes in the output |
| [`docs/rules/`](docs/rules/) | One specification per rule |
| [`docs/GLOSSARY.md`](docs/GLOSSARY.md) | Terminology and every report label |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Settled decisions and the reasoning behind them |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Where the project stands, and its kill criteria |
| [`AGENTS.md`](AGENTS.md) | Rules for AI agents working on this repository |

## License

Apache-2.0. See [`LICENSE`](LICENSE).

proof-java is not affiliated with, endorsed by, or derived from SonarQube,
JaCoCo, or PIT. Those names appear only to describe what this tool reads.
