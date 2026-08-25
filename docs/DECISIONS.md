# Decisions

Short entries; newest last. A reversal or correction gets a new entry, never a
silent rewrite. A superseded entry may be condensed and given a forward pointer
to the entry that revises it; its full original text stays in git history.

## Decided

**D-01 · CLI first, single executable jar** (2026-08)
Every surface consumes the CLI's JSON. The schema precedes renderers and is
explicitly experimental during 0.x; breaking changes increment its major and
need a decision. No IDE/CI/agent surface starts before v1 schema stability.

**D-02 · Verdict layer over engines, never own engines** (2026-08)
JaCoCo for coverage, git for diffs, PIT/Descartes for mutation. coverdict parses
their outputs and adds interpretation. Re-implementing an engine is out of
scope permanently.

**D-03 · Core in Java 17** (2026-08)
Measured: per-test analysis by shelling out to `jacococli` costs ~309 ms/test
(JVM startup); 5 000 tests ≈ 25 min by extrapolation. In-process JaCoCo removes
that process boundary; the earlier ~60× estimate is not measured (D-18). A JVM
core also fits future build plugins. The Python prototype is reference only.

**D-04 · Explicit metric modes with parity requirements** (2026-08, renamed 2026-08)
Modes are `jacoco-line`, `strict` (corrected to `strict-line` by D-19), and
`sonar-compatible` (`(cb + LC) / (cb + mb + EL)`). Every percentage names its
mode. Parity: `jacoco-line` equals JaCoCo's LINE counter; `sonar-compatible`
matches the same-scope SonarQube UI within ±0.1. Never use bare `sonar`.

**D-05 · Single exclusion layer** (2026-08)
Exclusions filter the dataset once; all metrics are recomputed from it.
Exclusion globs use SonarQube's `sonar.coverage.exclusions` syntax so teams
maintain one list.

**D-06 · Redundancy semantics** (2026-08)
Strict subset never makes the subset a deletion candidate; the superset is the
eager candidate. Identical sets were originally called duplicate clusters, but
D-21 limits them to coverage-equivalent candidates. The prototype proved that
the naive subset heuristic flags well-designed focused tests.

**D-07 · No auto-delete, ever** (2026-08)
Default posture is report. Deletion suggestions require HIGH confidence and
are still suggestions.

**D-08 · v0.1 ships without redundancy detection** (2026-08)
v0.1 = static oracle rules + coverage/new-code/uncovered (L0+L1). Redundancy
(L2) ships only after calibration on real repositories reaches ≥90% precision
on HIGH findings. Shipping the riskiest feature first is the likeliest way to
lose user trust permanently.

**D-09 · Descartes runs as a separate process, never as a declared dependency** (2026-08, tightened 2026-08)
Descartes is LGPL-3.0; coverdict targets Apache-2.0 and adopts the conservative
policy clarified in D-20. It never appears in a build descriptor at any scope;
coverdict invokes a user-installed process and parses reports. JaCoCo EPL-2.0
distribution obligations and the full dependency inventory apply at release.

**D-10 · Static analysis uses JavaParser, not regex** (2026-08)
The prototype's regex scanner is demo-grade. Real code (custom assertion
DSLs, parameterized tests, nested classes, Lombok) requires an AST.

**D-12 · Diff-scoped mutation testing is not free from open-source PIT** (2026-08)
Correction to earlier assumption: PIT's `scmMutationCoverage` goal was
removed. L3 must either build git-diff to PIT target mapping or treat ArcMutate
as an optional paid integration. Decide in M5 planning; until then L3 is not
advertised as diff-scoped.

**D-13 · Per-test coverage: dual-profile sequential analysis run** (2026-08)
JaCoCo probe state is process-global; overlapping tests race resets. The
candidate is an opt-in sequential analysis profile while normal builds remain
parallel. Thread-local probes are rejected. D-18 makes attribution and timing
provisional until a real-repo spike covers lifecycle and contamination risks.

**D-11 · Output contract designed for two readers** (2026-08)
Humans get text/HTML; agents get JSON with stable rule ids
(`NO_ORACLE`, `TAUTOLOGICAL_ORACLE`, `ORACLE_IN_CATCH`, `NULL_CHECK_ONLY`,
`DUPLICATE`, `EAGER_TEST`), locations, confidence, and suggested actions.
D-15/D-17/D-21 revise timing and names. Every surface renders the same JSON.

**D-14 · M0 readiness gate before production code** (2026-08-23)
M1 does not start until the primary workflow, three dogfood repositories,
input/error contract, JSON Schema, compatibility matrix, and validation
protocol are written. Until then only bounded contract spikes are allowed.

**D-15 · v0.1 is an evidence wedge, not the full mission** (2026-08-23)
M1/v0.1 delivers changed-code coverage plus a conservative static oracle
critic. It cannot quantify how much coverage is “real” or fuse per-test
evidence; those claims require L2/L3. v0.1 ships JSON and text; HTML is later.

**D-16 · Diff and source identity are explicit** (2026-08-23)
M1 uses merge-base diff semantics and records resolved commits and dirty state.
Each report binds a module root; identity is module id plus repo-relative path.
Missing/ambiguous mappings or nonexcluded untracked Java make the run incomplete
and non-zero, never silently absent. Other untracked files are warnings.

**D-17 · Static findings state only observed evidence** (2026-08-23)
D-11's `NO_ORACLE` becomes `NO_RECOGNIZED_ORACLE`; `ORACLE_IN_CATCH` becomes
`CATCH_ORACLE_WITHOUT_FAIL`. Confidence is detector certainty, separate from
severity. Unresolved symbols/helpers cannot produce HIGH absence findings;
supported and custom oracle APIs are explicit configuration.

**D-18 · D-13 attribution and timing are provisional** (2026-08-23)
Sequential execution removes overlapping probe resets; it does not prove exact
attribution across lifecycle, static state, async work, retries, or child JVMs.
The 18–22 minute and ~60× figures are models, not in-process measurements. M2
requires a recorded real-repo spike before D-13 becomes an architecture.

**D-19 · `strict` corrected to `strict-line`** (2026-08-23)
D-04's intended “no partial instruction coverage” mode is `ci > 0 && mi == 0`.
The prototype also required `mb == 0`, silently adding branch completeness; its
56.3% result does not validate `strict-line`. Recompute before publishing it.

**D-20 · D-09 is project policy, not ASF jurisdiction** (2026-08-23)
coverdict is not an ASF project; ASF Category X is not governing law here. We
voluntarily adopt its conservative distribution boundary: no LGPL dependency
at any scope, and Descartes remains user-installed and out of process.

**D-21 · Coverage identity is a candidate, not equivalence** (2026-08-23)
D-06's identical-coverage clusters become `COVERAGE_EQUIVALENT_CANDIDATE`.
Identical probe sets and syntactic oracle subsets do not prove behavioral or
oracle equivalence and cannot suggest deletion without stronger evidence.

**D-22 · coverdict is OS-independent by design** (2026-08-23)
Java CLI jar, JaCoCo XML, git, and JavaParser have no OS-specific code path;
Windows/macOS/Linux are all first-class. The one known platform-sensitive
area is path handling, already required as an automated test in M1c
criterion 7. No decision changes this unless a real OS-specific blocker appears.

**D-23 · O-04 resolved: Maven-first** (2026-08-23)
All three M0 dogfood proxies (commons-lang, Gson, Dropwizard — see
`docs/M0-PERSONA.md`) are Maven, as is the M1c corpus's multi-module member.
M3's first build integration is Maven; Gradle waits for demand evidence.
M1's CLI stays build-tool-agnostic (D-01/D-02).

**D-24 · Recognized oracle APIs include JUnit 4 and JUnit 5 from v0.1** (2026-08-23)
The A4 allowlist covers JUnit 5 (Jupiter) and JUnit 4 (`org.junit.Test`,
`org.junit.Assert`) plus AssertJ, Mockito verification, and Hamcrest. Gson's
JUnit 4-heavy suite is a dogfood target, so JUnit 4 is in scope from the
start. TestNG stays out until a dogfood repo forces it.

**D-25 · Schema fix: `inputs.modules` may be empty** (2026-08-23)
Building the M1a JSON writer surfaced a real gap: a run that fails before any
module's evidence is usable (e.g. every declared report is malformed) must
still emit a schema-valid incomplete document. Fabricating a placeholder
module to satisfy `minItems: 1` would violate hard rule 3a. The schema's
`inputs.modules` minItems constraint is removed; existing goldens still
validate since relaxing a constraint cannot break a previously-valid document.

**D-26 · Diff acquisition is config-independent and never guesses staleness** (2026-08-24)
`GitClient` passes `--find-renames`, `--no-color`, and explicit
`--src-prefix=a/ --dst-prefix=b/` on every diff call so output never depends
on the caller's own `~/.gitconfig`. `dirty` (D-16) means tracked changes only
(`git diff --quiet HEAD --`); untracked files are a separate, always-reported
concern. A changed line absent from an otherwise-mapped file's report (stale
report) is counted and warned, never inferred from file mtime — a timestamp
would break byte-determinism across checkouts and is easy to spoof.

**D-27 · Five-way changed-file classification, checked in a fixed order** (2026-08-24)
`unknown`/`excluded`/`non-executable`/`mapped`/`unsupported` (M0-CLI-INPUT.md),
resolved module-first (longest declared root wins), report-presence before
name-based rules (`module-info.java` sometimes has real report data - the
name is only a fallback excuse for zero data). A changed test file is
`excluded`, not incomplete: JaCoCo does not report test code, so its absence
is not missing evidence. Schema's `changedFile.module` is no longer required
- a path outside every declared module root is the one case with no valid
value for it (D-25 precedent: relaxing a constraint cannot break a
previously-valid document).

**D-28 · Oracle findings scope, and two-tier call resolution without a classpath** (2026-08-24)
`--findings-scope` (`all` default, `changed`) is always written to
`inputs.findingsScope` - schema addition, `changedFile`-style: an empty
`findings` array must never be ambiguous between "nothing wrong" and
"nothing scanned" (`changed` requires a diff mode, rejected under `--no-vcs`).
Oracle-allowlist call resolution tries Symbol Solver first (works for JDK
calls and same-file user types with zero jars), then falls back to
import-anchoring: a bare call matching one `import static` (or a qualified
call whose scope matches one `import`) is HIGH-confidence resolved by Java's
own naming rules alone, no classpath needed - the realistic path for a v0.1
user who has not configured one yet. Also fixed a real, reproducible bug this
surfaced: JavaParser's own primitive-type resolution does a default-locale
case conversion, and this project's own dev machine (Turkish default locale)
corrupts `"INT".toLowerCase()`, silently degrading confidence; forced
`Locale.ROOT` once per process (`OracleRuleEngine`'s static initializer).

**D-29 · M1c-1 hardening: three security-policy gaps closed, duplicate-id
rejection, findings-cap semantics, two-tier goldens** (2026-08-24)
SECURITY-POLICY.md §6 ("a clause without a failing-input test is treated as
unimplemented") cuts both ways: `REPORT_TOO_LARGE` (§2, a report over 256 MB
rejected via `Files.size` before the file is opened for streaming),
`PATH_ESCAPES_REPO_ROOT` (§4, a pure string check on a JaCoCo `<package
name>`/`<sourcefile name>` value before it is ever joined with a source root
and resolved), and terminal control-character escaping (§4, `TextRenderer`)
were spec'd but unimplemented; all three are now real, each behind a
negative test. A repeated `--module`/`--source-roots`/`--test-roots` id is
now `CliUsageException` (exit 2) rather than "last one silently wins" - hard
rule 3a applied to the CLI parser itself; `--report` is exempt, since
repeating a module id there is the legitimate multi-report-per-module case.
`OracleRuleEngine`'s findings cap is checked per file, not per finding: a
truncated run's file results are always whole, never split mid-file, at the
cost of the total count possibly exceeding the cap slightly - documented as
intentional, not a bug. M1c criterion 1 gets a second golden layer alongside
the M0 hand-written `schema/examples/`: two files under `fixtures/verdicts/`
are real CLI output from a synthetic in-repo fixture, compared byte-for-byte,
with a checked-in `${tool.version}` placeholder substituted at comparison
time (the only non-analytic field). Git commit SHA determinism for the
base-ref golden was verified empirically (fixed `GIT_AUTHOR_DATE`/
`GIT_COMMITTER_DATE`/identity reproduces the identical SHA across separate
directories and repeated runs on this machine) before committing to it - the
plan's documented fallback (restrict checked-in goldens to `--no-vcs`, prove
diff modes only via two-run byte equality) was not needed.

**D-30 · M1c-2 phase 1: corpus harness design, edition limit, calibration
round 1 result** (2026-08-24)
Three reusable PowerShell/Python scripts (`validation/scripts/`) drive every
M1c-2 phase: `run-corpus-phase.ps1` (clone/checkout/JaCoCo-bind/coverdict-run),
`sonar-parity.ps1`, `benchmark-phase.ps1`, plus `scaffold_labels.py` for the
labeling-protocol sample. Exercised end to end on the gson canary; phases
2-4 are parameter changes, not new code. JaCoCo is bound via CLI goals
(`prepare-agent`/`report`), never a permanent pom edit - a corpus repo's own
hardcoded `<argLine>` (gson precedent) may need a local, throwaway patch to
its *cloned* pom only, never committed. `sonar-maven-plugin:sonar` must run
from inside the target module's own directory, not via reactor `-pl`
(`-pl` + `:sonar` fails with "Maven session does not declare a top level
project" on this scanner version) - `sonar-parity.ps1` takes a module
directory, not a repo root. The local SonarQube instance is confirmed
**Community Edition**: branch/PR analysis is unsupported, so M1c criterion 2
is measurable only for **overall** scope until a Developer Edition instance
is available or the corpus's own git history is used to simulate a PR (not
attempted here) - documented as an open limit, not a silent pass (hard rule
3a). Calibration round 1 (kill criteria) on gson: `NO_RECOGNIZED_ORACLE`
HIGH-tier precision is 4.2% (4/96, seeded sample, seed 42) against a
required ≥90%, with a single dominant, confirmed root cause across every
false positive - Google Truth (`com.google.common.truth.Truth.assertThat`)
is absent from the D-24 allowlist. The rule is not removed or downgraded on
one round; the fix is scoped (extend D-24) and deferred to a future session,
with round 2 defined as re-running this same seeded sample afterward.

**D-31 · D-24 extended: Google Truth is a recognized oracle** (2026-08-24)
D-30's calibration round 1 traced its entire false-positive population to one
cause: Truth (`com.google.common.truth.Truth.assertThat`/`assertWithMessage`)
absent from the allowlist. Added with the exact same chain-anchor mechanism
already used for AssertJ (`OracleAllowlist.isChainAnchor`) - `assertThat(x).
isEqualTo(y)` and `assertWithMessage(msg).that(x).isEqualTo(y)` both resolve
via the existing outermost-chain-call walk, no new resolution logic needed.
Fixture harness (`pom.xml`'s `copy-fixture-harness`) gained a
`com.google.truth:truth:1.4.5` jar (gson's own pinned version) alongside the
other four libraries, proving SOLVED-tier resolution the same way; gson's
own real usage (static-imported `assertThat`) exercises the IMPORT_ANCHORED
tier instead, already correct by construction. While touching
`CATCH_ORACLE_WITHOUT_FAIL`'s fixtures, found and closed a real pre-existing
gap: the rule spec's own documented exemption ("never fires on: oracle
present after the whole try statement") had zero fixture coverage before
this - the exact gson finding (`ConcurrencyTest`) that motivated this entry
was that exemption failing to engage only because Truth was invisible.
**Not done here, intentionally:** extending `NULL_CHECK_ONLY`/
`TAUTOLOGICAL_ORACLE` with Truth-specific weak-oracle patterns (e.g.
`assertThat(x).isNotNull()`) - round 1's findings never touched those two
rules, so this is new scope needing its own spec text and fixtures, not a
fix for anything broken; left as a ROADMAP backlog line.

**D-32 · Calibration round 2 (post-D-31) passes; two new, narrower gaps
found and deferred** (2026-08-24)
Re-running D-30's gson canary after D-31: findings dropped from 1090 to 38
(-96.5%); all 38 reviewed (population under the 100-per-rule cap).
`NO_RECOGNIZED_ORACLE` HIGH-tier precision is 94.3% (33/35) - **meets the
≥90% bar, kill criteria round 2 of 2 closes with a pass.** Two new false
positives (not the Truth gap) trace to `OracleRecognizer`'s helper
traversal only following **private** same-compilation-unit helpers, not
public static ones (`DefaultTypeAdaptersTest`/`SqlTypesGsonTest`'s
`assertEqualsDate`/`testNullSerializationAndDeserialization` 2-arg
overloads). Three `CircularReferenceTest` findings are `INCONCLUSIVE`
because JavaParser's symbol solver cannot resolve an overloaded call whose
argument is a lambda (`assertThrowsStackOverflow(() -> ...)`) - the engine
correctly hedges rather than claiming a false HIGH (D-17), so this is
correct behavior under genuine uncertainty, not a wrong verdict. Both gaps
are real and distinct from D-31's fix; neither is implemented this session
- backlogged in ROADMAP.md as their own follow-up items. Round 1 also
had one of its own hand-labels corrected: `PerformanceTest#
testStringDeserialization`'s confirmed true positive turned out to be a
labeling error (the labeler missed that its private helper already
contained real Truth assertions) - noted for the record in
`validation/runs/gson/round2/precision-summary.md`, not silently rewritten.

**D-33 · Both D-32 backlog gaps closed: broader helper traversal, plus a
same-file name-match fallback** (2026-08-24)
Two fixes to `OracleRecognizer`, both sharing a new `isLocalHelperCandidate`
predicate (private-or-static, same compilation unit). (1) The existing
helper-traversal condition (`decl.isPrivate()`) is broadened to
private-**or**-static - a same-file `public static` test-support helper
(gson's `DefaultTypeAdaptersTest.assertEqualsDate`/`assertEqualsTime`) is
now followed, closing both round-2 false positives. (2) A minimal
JavaParser reproduction (real `junit.jar`, `ReflectionTypeSolver`)
disproved the round-2 hypothesis that lambda **arguments** caused
`CircularReferenceTest`'s `INCONCLUSIVE` findings: an external/unresolvable
**parameter type** on the callee's own declaration fails call resolution
identically whether the call site's argument is a lambda or not - the
lambda was never the variable. `resolve()` gained a third, narrow fallback
for exactly this: a bare (unqualified) call whose full resolution and
import-anchoring both fail now also tries a same-compilation-unit name
match against private-or-static declarations; exactly one match resolves
it, more than one (real ambiguity) or a qualified call leaves it
unresolved rather than guessing. Verified on the real gson checkout: 38
findings drop to 33, all HIGH, all true-positive per round 2's own review
(100% of what was reviewed, up from 94.3%) - the one finding that
correctly remains (`SqlTypesGsonTest#testNullSerializationAndDeserialization`)
is a genuine cross-file case, a different, still-open D-17 boundary
neither fix touched. `docs/rules/README.md`'s "Helper traversal" section
updated to match. See `validation/runs/gson/round2-followup.md`.

**D-34 · M1c-2 phase 2 (assertj): allowlist entry completed by prefix, not
a new library; corpus harness made reproducible** (2026-08-24)
A separate session cloned/built assertj and reported script improvements
that do not exist in this repo (`git diff HEAD -- validation/scripts/
run-corpus-phase.ps1` is empty, single branch, no stash) - its 5343-finding
output was real (verified against the on-disk `jacoco.xml`) but the harness
that produced it was not reproducible from the repo, and it used JaCoCo
0.8.15 with no rationale recorded anywhere (our own pin is 0.8.13). Root
cause of the finding volume, measured (not assumed) by reading real flagged
bodies against the real source: 41.8% call `BDDAssertions.then*`, 10.5% call
`Assertions.assertThatXxx*` beyond the 4 names already in D-24 - AssertJ is
already an allowlisted library; both classes declare a whole family of
typed-subject entry points sharing one prefix (`Assertions` declares 27
`assertThatXxx` methods, `BDDAssertions` 29 `thenXxx`), so `OracleAllowlist.
isChainAnchor` now matches by prefix for these two types - the same style
already used for JUnit's `assert*`/`fail` in `isUnconditionalOracle`. Not a
new-library decision; this is D-24's existing AssertJ entry, completed.
3.1% call `AssertionsUtil.assertThatAssertionErrorIsThrownBy`, a test-side
cross-file helper (D-17 boundary, same category as gson's `MoreAsserts`) -
deliberately left unrecognized. 5.2% use `SoftAssertions` instance receivers
(`softly.assertThat(...)`) - out of scope this round, `isChainAnchor` is
built on static (declaringType, methodName) pairs and instance-receiver
recognition is a structural change to `OracleRecognizer`, not an allowlist
edit; backlogged. Result measured on the real corpus: 5343 to 2148 findings
(-59.8%). `run-corpus-phase.ps1` gained multi-patch support (`PomPatches`,
a list of (search, replace) pairs - assertj needed two, not one), a
git-reset-hard-and-clean before every build so patches never stack across
repeated runs, an explicit `mvn.cmd` invocation, and a `-SkipBuild` mode to
re-run the analyzer alone against an already-produced `jacoco.xml` without
rebuilding a 14,701-test suite. The JaCoCo 0.8.15 deviation from our 0.8.13
pin is accepted as-is (rebuilding assertj's full suite solely to change a
patch-version JaCoCo build serves no purpose - the manifest's own position
is that the XML report format is the contract, not the tool version) but is
now recorded, not silent. Precision labeling (criterion 4, seed 42, 100
`NO_RECOGNIZED_ORACLE` sampled + the full 2-item `CATCH_ORACLE_WITHOUT_FAIL`
population): **100% true-positive, calibration round 1 passes** - unlike
gson, no second round is warranted. Root cause of the profile difference:
much of assertj's own suite tests its own internal assertion engine via
void calls relying on non-throw (no missing-library gap to find). Found and
backlogged, not fixed this session: `WithAssertions` interface delegation
(a class `implements WithAssertions` gets `assertThat` resolved to that
interface's own default methods, a different declaring type) and
`InstanceOfAssertFactory` chains (terminal assertions on a factory-returned
assert object, never touching `Assertions`/`BDDAssertions`).
`validation/runs/assertj/precision-summary.md`.

**D-35 · WSL2 memory cap required before scanning a large corpus; local
SonarQube CE completion must be polled, not slept** (2026-08-24)
The first `sonar-parity.ps1` run against assertj-core (~4600 test files, 4x
gson's size) triggered a real, severe incident on the development machine:
`C:\Users\Mert\.wslconfig` had no `[wsl2] memory=` cap, so the local
Docker-Desktop-hosted SonarQube server's WSL2 VM balloon grew unbounded
while ingesting the large analysis report, consumed enough of the
machine's 32 GB RAM to freeze Windows entirely, and required a hard
restart - twice, since a retry before diagnosing the cause repeated it.
Root cause confirmed via Task Manager (`VmmemWSL`/`OpenJDK Platform
binary` at 70%+ CPU, sustained) and the missing config line. Fix (applied
by the user, not this session): `memory=8GB` added to `.wslconfig`,
followed by `wsl --shutdown` and a Docker Desktop restart. Verified safe
by re-running the same assertj scan afterward - `VmmemWSL` stayed under
the cap, no incident. **Any future corpus phase (junit-framework,
dropwizard - both likely larger than assertj) requires this cap to already
be in place before a Sonar scan is attempted; never attempt one on a
machine without a WSL2 memory ceiling confirmed first.** Separately (a
real but far lower-severity bug hit while investigating): `sonar-parity.ps1`
slept a fixed 5 seconds after the scanner exited before reading measures -
correct for gson's size, but SonarQube's Compute Engine was still
`IN_PROGRESS` processing assertj's larger report past that window, so the
API silently returned empty measures rather than an error (a false `FAIL`
in the generated report, since deltas involving empty compared to a real
number are large). Fixed to poll `api/ce/component` until its `queue` is
empty instead of guessing a sleep duration.

**D-36 · M1c-2 phase 3 (junit-framework): Gradle branch added to the corpus
harness; module scoped to junit-vintage-engine; a chain-terminal oracle
recognition fix was attempted and reverted; sonar-parity deferred**
(2026-08-24)
`run-corpus-phase.ps1` gained a `-BuildTool Gradle` branch (`-GradleModule`,
`-GradleInitScript`) alongside the unchanged Maven branch (regression-proven:
gson still 33 findings, assertj still 2148, both re-run through the extended
script). The default init script turns on `JacocoReport` XML output (off by
default in Gradle's own jacoco plugin) via `gradle.beforeProject` rather
than `allprojects{}` - junit-framework has
`org.gradle.isolated-projects=true` in its `gradle.properties`, which
rejects any cross-project `Project.plugins` access through `allprojects{}`
in both the outer build and its `gradle/plugins` included build;
`beforeProject` configures each project from within its own configuration
phase, which Isolated Projects allows. `--no-daemon` is used deliberately
(D-35 lesson: a one-shot corpus run should never leave a background JVM
resident). The repo's `gradle-daemon-jvm.properties` requires JDK 25; the
user installed Temurin 25 via winget (a real environment prerequisite, not
worked around silently, since Gradle refused to auto-download it - no
toolchain repository is configured for Windows/x86_64 in this repo).

**Module scope**: `junit-team/junit-framework` splits every Jupiter/platform
component into a main-only module (no local tests) with all tests
centralized in separate `jupiter-tests`/`platform-tests` modules, bound via
JaCoCo's whole-repo aggregation plugin - incompatible with coverdict's
one-module-owns-its-own-main+test model, and would require building/testing
30+ modules just to analyze one. `junit-vintage-engine` is the only
component with its own self-contained `src/main/java`+`src/test/java`, and
does contain real `@Nested`/`@TestFactory`/`DynamicTest` usage (verified
before committing to it) - a deliberate, documented scope narrowing, not
avoidance of difficulty.

**Precision result**: 51 `NO_RECOGNIZED_ORACLE` findings (full population).
HIGH 1/1 (100%, the manifest's hard kill-criterion, met trivially at n=1).
MEDIUM 0/46 and INCONCLUSIVE 0/4 - both reported in full, not hidden. 82% of
all findings (42/51) trace to one root cause: JUnit Platform Testkit's
`Events.assertEventsMatchExactly`/`assertEventsMatchLoosely` is the
**terminal** link of a chain rooted at the test's own private helper
(`execute(x).allEvents().assertEventsMatchExactly(...)`), not a call the
library itself anchors - `OracleRecognizer.isChainAnchor` only ever
inspects the chain's root, by design (every prior recognized library -
AssertJ, Truth, JUnit4/5, Mockito BDD - owns its chain's root call). The
remaining 8 findings trace to `OracleRecognizer.sameFileNameMatch`'s
ambiguity bailout not considering call-site arity when two same-named
same-file helper overloads exist (`assertYieldsNoDescriptors`,
`doesNotResolve`) - a real, separate, narrower gap. Full root-cause
breakdown and per-item reasoning: `validation/runs/junit-framework/
precision-summary.md` and `labels.csv`.

**A same-session fix attempt for the chain-terminal gap was implemented,
verified against the fixture harness (a new `testkitEventsChainTerminal`
fixture, `junit-platform-testkit` added to the fixture-harness jar list),
regression-clean on gson/assertj/coverdict's own suite - and then reverted**
after real-world testing against junit-vintage-engine showed it does not
actually help: coverdict's real `analyze` path has no `--classpath`
mechanism at all (only source-roots + JDK reflection + the file's own
import statements - the fixture harness's `JarTypeSolver` is a test-only
mechanism, never present for a real corpus run), and the affected test
files never literally `import` the JUnit Platform Testkit types whose
methods form the chain's terminal (`Events` is only ever an inferred
chain-return type, never referenced by name) - so there is no fact, resolved
or import-anchored, connecting the terminal call back to its owning type.
The only remaining path is a name-only heuristic (trust
`assertEventsMatch*` with zero type evidence) or real classpath support (an
M2/M3-scale feature) - both correctly out of scope for a measurement phase,
so the code was reverted to HEAD rather than shipped half-verified (hard
rule: unknown is never silently green). Backlogged, not fixed - see
`docs/ROADMAP.md`.

**Sonar parity (criterion 2) deferred for this phase**: `sonar-parity.ps1`
is Maven-only (invokes `sonar-maven-plugin:sonar` from inside the module
directory, which requires a `pom.xml`). junit-framework has none. A Gradle
equivalent needs its own design - `org.sonarqube`'s Gradle plugin applies at
the root and aggregates the whole project tree by default, and this repo's
Isolated Projects mode (same wall hit above) makes scoping it down to just
`junit-vintage-engine` non-trivial - forcing an ad hoc version now, on a
30+-module tree, is exactly the kind of unscoped-operation risk D-35 exists
to avoid. Left undone rather than rushed.

**Benchmark (criterion 6) done, but hit and fixed a second real,
pre-existing script bug**: `benchmark-phase.ps1` redirected the child
`java` process's stdout/stderr but never drained them - harmless for gson/
assertj's short output, but junit-vintage-engine's 51-finding analyze
output was large enough to fill the OS pipe buffer and deadlock the child
process forever (observed directly: the benchmark hung indefinitely on its
first "Cold run", confirmed via `tasklist` still showing the `java.exe`
alive with no progress). Fixed with `BeginOutputReadLine`/
`BeginErrorReadLine` (verified this drains the pipe without needing an
attached event handler, via a standalone repro). Results: cold 1909.4 ms,
warm median 1918.6 ms, warm p95 1937.9 ms, peak working set 425 MB - far
higher than gson's (207.2 ms / 56.9 MB) despite a *smaller* analyzed source
tree, because `--repo` points at the whole junit-framework checkout (a
30+-module monorepo) even though only one module's files are in scope -
likely file-system/module-graph overhead scales with repo size, not just
analyzed size. Not investigated further this session (out of scope for a
measurement phase); worth a closer look before trusting benchmark numbers
across repos of very different total size.

**D-37 · M1c-2 phase 4 (dropwizard): real multi-module binding added to the
corpus harness; `dropwizard-util` + `dropwizard-validation` chosen; 0
findings; multi-module sonar parity PASS on the first attempt** (2026-08-25)
`run-corpus-phase.ps1`'s `-ModuleId`/`-ModuleRoot` scalars became
`-ModuleIds`/`-ModuleRoots` arrays: one `mvn -pl modA,modB -am` reactor
build, one jacoco.xml resolved per module, and every `--module`/
`--source-roots`/`--test-roots`/`--report` flag repeated once per module in
a single coverdict invocation - proving `ModuleBinder`'s real multi-module
path for the first time (previously only unit-tested via a degenerate
same-root case, see `ModuleBinderTest`). `sonar-parity.ps1` gained the same
shape (`-ModuleDirs`/`-JacocoXmlRelativePaths` arrays): more than one module
switches it to a reactor scan (`-pl <dirs> -am sonar:sonar`, no per-module
`sonar.sources`/`sonar.tests` override - each submodule's own pom supplies
its layout, Sonar attributes each XML's lines to whichever module's source
tree they fall under). The known single-module `-pl` + `sonar:sonar`
"Maven session does not declare a top level project" failure (gson/assertj
era) **did not reproduce** here - `dropwizard-util`/`dropwizard-validation`
are real `<module>` entries of the root aggregator pom, unlike the earlier
phases' module directories, so the standard reactor-scan path applied
cleanly. `benchmark-phase.ps1` needed no change (`-JarArgs` was already
generic).

**Module choice**: `dropwizard-util` (7 test files, no `dropwizard-*`
dependency - the reactor's leaf) and `dropwizard-validation` (10 test
files, depends on `dropwizard-util` at compile scope) - a real dependency
edge, not two arbitrary unrelated modules, and small enough (105 `@Test`
methods total) to stay well clear of the D-35 memory risk that hit assertj.
`maven.compiler.release` is 11 here, not the harness's 17 default -
`-LanguageLevel 11` passed explicitly; JaCoCo is `0.8.14`
(`dropwizard-dependencies`), not the harness's 0.8.13 default.

**Result**: 0 findings across every rule and confidence tier, over all 105
`@Test` methods (`analysis.status: complete`, no incomplete reasons) - both
modules' tests are ~1083 `assertThat(...)` (assertj) plus 2 Mockito
`verify(...)` calls, all already covered by D-34's allowlist, with zero
`@Disabled`/`@Ignore` methods. Criterion 4's HIGH bar is met vacuously (n=0),
the same shape as junit-framework's n=1 - a real result, corroborated by
non-trivial coverage numbers proving the scan executed, not a silent gap
(see `validation/runs/dropwizard/precision-summary.md` for the full
reasoning). Sonar parity (criterion 2): exact match, 81.1 vs 81.1 (delta 0),
jacoco-line/line_coverage also exact (84.8 vs 84.8) as a bonus cross-check.
Benchmark (criterion 6): cold 4608.4 ms, warm median 4294.5 ms, warm p95
4443.1 ms, peak working set 603 MB - the highest of any phase despite the
smallest analyzed source tree (105 tests, 2 small modules), confirming the
open question D-36 flagged: `--repo` pointing at the whole 34-module
dropwizard checkout, not just the two bound modules, drives runtime/memory
overhead independent of analyzed size. Still not investigated further (out
of scope for a measurement phase).

**D-38 · dropwizard corpus pin has no JUnit 4 - manifest's "mixed JUnit 4+5"
forces claim corrected** (2026-08-25)
`docs/M0-VALIDATION-MANIFEST.md`'s phase-4 row listed dropwizard's forces as
"multi-module Maven, mixed JUnit 4+5, report-to-module binding". Checked
directly against the pinned checkout: `git grep` for
`import org.junit.Test;`, `@RunWith`, `import org.junit.Rule;`, and
`import static org.junit.Assert` across the whole repo returns zero matches;
every module pom explicitly `<exclusion>`s `junit:junit`; all 321 `@Test`
methods repo-wide are `org.junit.jupiter.api.Test`. `release/4.0.x` is fully
migrated to JUnit 5 - the manifest's claim is stale, likely predating that
migration. Corrected to "multi-module Maven, report-to-module binding" in
the manifest; JUnit 4 handling stays validated by gson (phase 1) and the
allowlist's own D-24 coverage, not by this phase.

**D-39 · `--classpath` implemented; per-module ids accepted but solvers are
unioned** (2026-08-25)
`M0-CLI-INPUT.md`'s `--classpath <id>=<file>` was specified in M0 and never
built - D-28 shipped import-anchoring precisely because a real run had no
classpath, and D-36's chain-terminal fix was later abandoned for the same
reason. Now real: `ClasspathLoader` reads each list file (one jar path per
line, `#` comments and blanks skipped), builds a `JarTypeSolver` per jar, and
hands them to the `OracleRuleEngine.scan` overload that already existed as a
test-only hook for the fixture harness. That overload is now public and its
javadoc no longer claims it is "never populated in production".

**Failure handling is warn-and-continue, never fail-closed**, because D-17's
direction is one-way: an unreadable list (`CLASSPATH_FILE_UNREADABLE`) or an
entry that will not open as a jar (`CLASSPATH_ENTRY_UNUSABLE`) leaves
resolution exactly where D-28's two tiers already had it, so it is a warning,
not an incomplete run. An `--classpath` id naming no declared `--module` is
the opposite case - exit 2, because the user would otherwise believe a
classpath was in effect when nothing bound it (hard rule 3a). A jar named by
two modules is opened once.

**Known narrowing, recorded rather than assumed:** the option's surface is
per-module (`<id>=<file>`), but `JavaSourceParser` builds one
`CombinedTypeSolver` for the whole run, so every declared module's jars are
visible to every module's parse. Splitting that into a parser per module
changes the scan loop and buys nothing for v0.1's allowlist (the recognized
libraries are test-scope dependencies shared across a repo's modules). The id
is still required and still validated, so the argument stays checkable.

Not claimed: this does **not** by itself close D-36's chain-terminal gap. It
removes the *mechanism* blocker (there is now a way to put `junit-platform-
testkit` on the solver's path), but D-36's second finding stands - the
affected junit-vintage-engine files never name `Events` in an import either,
so whether a classpath actually resolves that chain is an open measurement,
not a fixed bug. Re-measuring it is corpus work, not this change.

**D-40 · `--config` implemented with a hand-written strict reader, not the
schema validator** (2026-08-25)
`M0-CLI-INPUT.md` specified `--config <path>` (falling back to
`coverdict.config.json` at the repo root) in M0 and it was never built - which
also silently blocked `customOracles` and `suppressions`, since both are
config-file features. The gap was not recorded anywhere; only an incidental
javadoc aside in `JacocoXmlParser` mentioned it.

`schema/coverdict-config.schema.json` is checked in as the contract, but the
**reader is hand-written against `jackson-core`** rather than pulling
`json-schema-validator` (today test-scope) into the shipped jar: hard rule 9
makes every runtime dependency a licensing and inventory obligation, and that
is a poor trade for one small fixed-shape file. The risk this creates - schema
and parser drifting apart - is covered by
`ConfigLoaderTest.theCheckedInSchemaAndTheHandWrittenReaderAgree`, which runs
the same documents through both and requires identical accept/reject. That
guard **caught a real drift on its first run** (the schema restricted
`suppressions.rule` to the four rule ids, the reader accepted any string),
which is why `RuleIds` now exists: one public set the four rule classes, the
reader, and the schema's enum all resolve against.

Reading is deliberately intolerant - unknown key, duplicate key, wrong value
type, malformed JSON, a `customOracles` entry that is not
`Type#methodPattern`, or a suppression missing its mandatory `reason` are all
exit 2. A misspelled `supressions` that silently suppressed nothing is exactly
the failure hard rule 3a exists to prevent.

Precedence (`M0-CLI-INPUT.md`: command line > config file > defaults) is
implemented by asking picocli's `ParseResult.hasMatchedOption` whether the user
actually typed the option, since the bound field alone cannot distinguish a
typed value from a default. `coverageExclusions` is **replaced, never merged**
with `--coverage-exclusions`: merging two half-lists would produce a third list
nobody authored, against D-05's single-authored-set model. An explicitly empty
list in the config is preserved as "no exclusions" and stays distinct from
"unset".

**D-41 · `customOracles` implemented; configured entries are unconditional
oracles, not chain anchors** (2026-08-25)
The last M0-specified oracle feature that had no implementation
(docs/rules/README.md's "Custom oracles"). Unblocked by D-40's config surface.
`CustomOracles` parses `fully.qualified.Type#methodPattern` entries and is
consulted next to `OracleAllowlist.isUnconditionalOracle`: **the configured
call is itself the oracle**, with nothing required to be chained onto its
result. A user needing the chain-anchor shape is describing a fluent assertion
library, which is a built-in allowlist question (D-34), not a per-repo helper.

Configured types also join the set import-anchoring searches. Without that, a
custom oracle would only be recognized when the Symbol Solver happened to
resolve it - which for a helper in another module with no `--classpath` is
precisely the case that fails, i.e. the feature would have worked everywhere
except where it is needed.

The method glob is matched by a hand-written scanner rather than by
translating `*` into a regex, so a pattern containing regex metacharacters
(a `$` from a nested-class-style name) cannot silently change what matching
means.

This closes the gap all four corpus phases labelled "D-17 territory" and
marked out of scope - gson's `MoreAsserts`, assertj's `AssertionsUtil`,
junit-framework's `PreconditionAssertions` are all this shape. Those phases'
precision numbers stand as measured: they recorded correct behaviour for an
unconfigured run, and re-measuring with configuration is corpus work, not part
of this change.

`OracleRuleEngine.scan`'s positional signature had reached seven parameters,
three of them optional hooks whose order only the compiler was checking, so
custom oracles and suppressions arrive via a new `OracleScanOptions` parameter
object instead of two more positions.

## Rejected

**R-01 · LLM-as-judge for verdicts** — non-deterministic, costs per run, not
reproducible in CI; also the crowded, undifferentiated corner of the market.
**R-02 · Building on OpenClover for per-test coverage** — source
instrumentation, experimental Java 17 support, no parallel execution.
**R-03 · Python core** — see D-03.

## Open

**O-02 · SARIF as an additional output format** — would give GitHub code
scanning and IDE problem-panel integration nearly free. Evaluate with CI work.
**O-05 · Build our own diff-scoped mutation mapping, or make ArcMutate an
optional integration** — see D-12. Decide at M5.
**O-07 · Kotlin/Android as a supported target** — D-10 (JavaParser) does not
extend to Kotlin syntax; would need its own AST/symbol-resolution frontend.
JaCoCo reads Kotlin bytecode fine, but inline functions copy code to call
sites and break line attribution; Android adds its own report layout,
flavor/variant matrix, and generated sources. Out of scope unless a dogfood
repo (M0 item 1) forces it.

Resolved: O-01 is **coverdict**; O-03 is D-13/D-18; O-04 is D-23; O-06 is D-04.
