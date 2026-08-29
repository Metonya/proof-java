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

**D-42 · `suppressions` implemented; suppressed findings are counted, and
`$defs/reason` gains an optional `count`** (2026-08-25)
docs/rules/README.md scopes suppression as "Configuration-only in **v0.1**"
(only baselines and changed-only gating are M3), so this was v0.1 debt, not
M3 work. `SuppressionFilter` withholds a finding when its rule, path glob and
optional `testMethodPattern` all match, and emits one `SUPPRESSED_FINDINGS`
warning carrying the count.

**The count is a schema field, not only prose.** `$defs/reason` gains an
optional `count` (additive, not breaking - no existing document changes, all
three goldens still validate byte-for-byte). Hard rule 7 makes the JSON the
product; a number an agent has to regex out of an English sentence is not a
contract. The alternative - embedding it in `message` alone - was rejected for
that reason.

Glob matching reuses `ExclusionFilter.compile`/`matchesAny` rather than
growing a second implementation, so one config file cannot end up with two
different meanings for `**`, and D-22's platform-independence argument (why
`FileSystems.getPathMatcher` is avoided) keeps covering this path too.

Suppression is applied **after** sorting and after the findings cap: it
changes what the reader is shown, never which files were scanned or where the
truncation boundary fell. An all-suppressed run therefore still reports a
non-zero count - an empty `findings` array must never be indistinguishable
from a genuinely clean run (hard rule 3a).

`validation/SHA256SUMS` is updated for the schema edit in the same commit -
the c4377d2 precedent is that forgetting this is easy and only surfaces later.

**D-43 · JUnit 4 `ExpectedException` and the `@Disabled`/`@Ignore` note
implemented; field-type anchoring added** (2026-08-25)
Two behaviours docs/rules/README.md specified in M0 that had **no
implementation and no recorded deferral** - the only two gaps found in this
sweep that were not documented anywhere. Both survived four corpus phases for
the same reason: gson was the only JUnit 4 repo and used neither shape, and
dropwizard's pin has no JUnit 4 at all (D-38). A validation corpus only proves
what it happens to contain.

`org.junit.rules.ExpectedException` joins the allowlist with an `expect*`
prefix (`expect`/`expectMessage`/`expectCause` each state a verification the
test must satisfy, the same reading that makes `@Test(expected=...)` an
oracle). Recognizing it needed a genuinely new resolution path: the call is
`thrown.expect(...)`, whose scope names a **variable**, not a type - so
`typeSingleImportOwner` misses, and with no `--classpath` the Symbol Solver
cannot resolve the library call either. `OracleRecognizer.fieldTypeOwner` now
looks the name up as a field of the same compilation unit and anchors its
*declared type* through the imports. Kept narrow on purpose (this
compilation unit's own fields, declared type must resolve through a real
import): an inherited field or an unimported type stays unresolved rather
than guessed at, per D-17. A `StringBuilder thrown` decoy is covered by test.

The `disabled test` note is applied centrally in `addIfPresent`, so it holds
for all four rules rather than one. Class-level `@Disabled`/`@Ignore` counts
too - it disables every method just as effectively. The spec's "still
analyzed" is the load-bearing half: skipping disabled tests would quietly
shrink the denominator (hard rule 3a).

**D-44 · Release artifacts are generated and gated by a `release` profile;
dual-license elections are recorded explicitly** (2026-08-25)
`RELEASE-CONTRACT.md` promised a generated `NOTICE`, a full transitive
dependency/license inventory, and a `SHA-256SUMS` per release, and stated that
"a release with a missing or stale inventory does not ship". None of it
existed: no `license-maven-plugin` in any pom, no NOTICE, no inventory, no
checksums, and M0 deliverable 7 was marked **Done** on `LICENSE` plus the
contract document alone. Nothing would have failed if a release had been cut.

`mvn -P release clean package` now produces `target/release/` containing the
shaded jar, a generated `NOTICE` (fixed header + the freshly generated
inventory, so it cannot go stale against the real tree), `THIRD-PARTY.txt`,
`LICENSE`, and a `SHA-256SUMS` that `sha256sum -c` actually verifies. It is a
profile, not the default build, because `mvn verify` runs on every change and
these goals resolve the full transitive tree.

**Two real problems surfaced while making the hard-rule-9 gate work, both
found by running it rather than by reading:**

1. `excludedLicenses` is a **literal pipe-separated list, not a regex**. The
   first widening attempt (`.*(LGPL|Lesser General Public).*`) matched nothing
   and the build passed - a gate that silently never fires, which is worse
   than no gate. Reverted to literals and proven by a negative test:
   removing the JavaParser election makes the build FAIL, restoring it makes
   it pass.
2. Ant's `<checksum>` task writes the bare digest with no file name, which is
   not a verifiable sumfile. The two-column format is now written explicitly,
   matching `validation/SHA256SUMS`'s layout so one `sha256sum -c` reads both.

**Dual-license elections** live in `license-overrides.properties` at the repo
root. JavaParser is `LGPL-3.0 OR Apache-2.0` and lists LGPL first, so the gate
fired on it - correctly, since nothing had ever recorded which option
coverdict exercises beyond a prose comment. Javassist turned out to be
*triple*-licensed (`Apache-2.0 OR LGPL-2.1 OR MPL-1.1`) and was passing only
because its pom happens to list Apache first; that election is now recorded
too, so an upstream reordering cannot silently change what coverdict ships
under. These are elections on genuinely multi-licensed artifacts, never a way
to silence the gate - a single-licensed LGPL dependency has no Apache option
to elect and still fails the build (D-20).

**D-45 · Gradle-phase sonar parity needed no Gradle design; D-36's deferral
is superseded** (2026-08-25)
D-36 left phase 3's criterion-2 parity undone, reasoning that `sonar-parity.
ps1` is Maven-only and "a Gradle equivalent needs its own design -
`org.sonarqube`'s Gradle plugin applies at the root and aggregates the whole
project tree by default, and this repo's Isolated Projects mode makes scoping
it down non-trivial". That framing assumed the scanner had to be driven
through the build tool. It does not: the standalone `sonar-scanner` CLI takes
`sonar.sources`, `sonar.tests` and `sonar.coverage.jacoco.xmlReportPaths`
directly - the same three facts coverdict itself takes, which is precisely
D-01/D-02's point that the host build tool is irrelevant to this analysis.

`sonar-parity.ps1` gained a `-Scanner Cli` branch. Phase 3 parity: **exact
match, 54.8 vs 54.8** (`jacoco-line`/`line_coverage` also exact, 58.8 vs
58.8), on the existing JaCoCo XML with no Gradle rebuild. Criterion 2
(overall) is now closed on all four phases, every one an exact match: gson
91.0, assertj 74.4, junit-framework 54.8, dropwizard 81.1.

Worth recording as a reasoning error, not just a result: the blocker was
never Gradle, it was reaching for the build-tool-integrated scanner by habit
when the tool-agnostic one matched the project's own stated input model. D-36
was correct not to rush an `org.sonarqube` integration; it was wrong that no
cheap path existed.

**New-code parity remains deferred** (user decision, same date): it needs
branch/PR analysis, which Community Edition does not support at any
configuration. Not a coverdict defect and not closable by work in this repo -
it is a follow-up for a Developer/Enterprise instance, and M1 does not wait
on it.

**D-46 · L2 is an evidence layer, never a standalone finding source**
(2026-08-25)
`JVM Per-Test Coverage Research.md` §D3 confirms coverage-overlap is
necessary but not sufficient for a redundancy claim: assertion diversity and
equivalence-partitioned parameterization both produce identical execution
fingerprints for genuinely distinct tests. `COVERAGE_EQUIVALENT_CANDIDATE`
(D-21's naming) is therefore never emitted from L2 evidence alone. It
requires all three: coverage overlap (L2), matching assertion structure
(L0's oracle recognizer), and matching mutant-kill sets (L3). M4
("L2 redundancy productization") is redefined around this three-stage gate,
superseding its ROADMAP text's "90% precision on HIGH findings" framing,
which was silent on what evidence composes a finding.

**D-47 · L2's engine is PIT's coverage-collection phase, not a sequential
JaCoCo reset profile** (2026-08-25)
Supersedes D-13's "opt-in sequential analysis profile with reset() per
test." M2 Faz 0 (`validation/runs/pit-spike/FINDINGS.md`) ran PIT 1.15.8's
mutation-coverage goal against coverdict's own `analysis.oracle.*` package
and against gson's `internal.LazilyParsedNumber`: PIT's coverage-collection
phase already builds a per-test line map internally (`LineMapper` +
`CoverageExporterFactory`, both real, documented PIT APIs), takes ~1 second
on coverdict's ~40-test-class scope, and needs no probe-reset choreography
of our own - PIT's own minion process handles isolation.

This is *not* "parse `linecoverage.xml`" as first hypothesized (D-12's
mention, and the research report's B1 claim) - that file exposes PIT's
internal block index, not source line numbers (see D-49's schema note).
The real integration point is `org.pitest.coverage.CoverageExporterFactory`,
a `ToolClasspathPlugin` SPI coverdict must implement and ship as a small jar
on PIT's tool classpath, using `LineMapper` to resolve each block to real
source lines before writing coverdict's own JSON. This is more invasive
than pure report-file parsing, but still consumes PIT's own public
extension point rather than reimplementing bytecode instrumentation -
consistent with D-02's "verdict layer over engines, never own engines," but
close enough to the line that the distinction is recorded here rather than
assumed. A working proof-of-concept (`validation/runs/pit-spike/
exporter-poc/CoverdictLineExporter.java`) exists and resolved 1996/1996
blocks to correct source lines on the first real target.

If a corpus repo cannot get PIT's coverage phase green within the M2 Faz 2
budget (a real risk - `mutationCoverage` refuses any red test, and D-45's
corpus repos carry their own build quirks, per `FINDINGS.md` §5's gson
`argLine` case), the fallback is D-13's original sequential-JaCoCo-reset
design, scoped to L1's changed-line set rather than the whole suite (never
the whole-suite design D-18 costed).

**D-48 · Delta-of-cumulative coverage snapshots are rejected outright**
(2026-08-25)
`JVM Per-Test Coverage Research.md` §C4 gives a short proof: taking
cumulative JaCoCo snapshots between tests without `reset()` and diffing
consecutive snapshots yields *first-toucher* coverage, not per-test
coverage - a probe already hit by an earlier test never appears in a later
test's diff even if that later test also executes it. This destroys both
Q1 (multiplicity - a probe hit by 50 tests appears to have multiplicity 1)
and Q2 (redundancy - two tests with identical coverage appear to share
nothing). No implementation of this shape is considered for L2.

**D-49 · Real `linecoverage.xml` schema corrects D-13's and the research
report's assumed shape** (2026-08-25)
The file's root element is a flat list of `<block classname='...'
method='...' number='N'><tests>...` - `number` is PIT's internal
control-flow-block index, not a source line, and there is no `<class>`/
`<line>` nesting. Test names are the full JUnit5 `UniqueId` string
(`[engine:...]/[class:...]/[method:...()]`) or, for JUnit4 targets, `Class.
method(Class)` - both are unambiguously method-level, which resolves A3's
granularity-collapse concern on the test-identity axis (it does not, by
itself, resolve line attribution - that is D-47/D-49's `LineMapper` point).
Static initializers surface as ordinary blocks with `method='<clinit>'`,
which is the natural key for D-50's ambient-bucket separation - no special
detection code needed.

**D-50 · Load-time coverage goes to an ambient bucket keyed by `<clinit>`,
never forced via reflective pre-loading** (2026-08-25)
The research report's recommended mechanism - reflectively `Class.forName()`
every project class before the suite runs, to move static-initializer
coverage into a baseline bucket - is rejected: forcing class initialization
can start threads, open connections, or throw `ExceptionInInitializerError`,
and a class that fails to initialize this way is unusable for the rest of
that JVM's life. The *policy* (static initializers should not count toward
per-test attribution) is accepted; the mechanism is not needed. D-49
confirms PIT's own block metadata already tags static-initializer coverage
with `method='<clinit>'` (and PIT's own `StaticInitializerFilter` already
identifies code reachable only from `<clinit>`), so the ambient bucket is a
filter on existing data, not new instrumentation.

**D-51 · L2's calling model is `EntryPoint`, not a build-tool plugin
profile - never touches the target repo's build files** (2026-08-25)
M2 Faz 0's spike drove PIT through a throwaway `pitest-maven` Maven profile
patched into the target module's `pom.xml` and reverted afterward
(`git checkout`) - workable for a spike, wrong for the product: it would
mean asking every coverdict user to accept a temporary build-file edit, and
it has no answer at all for a Gradle target (junit-framework, an M2 Faz 2
corpus repo). M2 Faz 2a verified `org.pitest.mutationtest.tooling.
EntryPoint.execute(File, ReportOptions, PluginServices, Map)` - confirmed
present, public, and functional in `pitest-entry:1.15.8` - drives the
identical coverage-collection phase with zero repo modification: `git
status` on coverdict's own repo stayed empty across the run. Same finding
as D-45 (the standalone `sonar-scanner` CLI beating the build-tool-integrated
scanner): the engine never needed the host build tool, only its own
inputs - classpath, source dirs, target class/test globs, report directory,
all of which `ReportOptions`'s setters take directly and all of which
coverdict already collects from `--classpath`/`--module`/`--source-roots`/
`--test-roots`.

Two non-obvious `ReportOptions` requirements found only by running it, not
documented anywhere in PIT's own javadoc:
- `setGroupConfig(TestGroupConfig.emptyConfig())` is mandatory - the Maven
  plugin sets a default a caller must replicate, or `createMinionSettings()`
  NPEs on a null field via `Objects.requireNonNull`.
- Every path handed to `setClassPathElements`/`setCodePaths`/`setSourceDirs`
  must be canonicalized (`File.getCanonicalPath()`). A path built by naive
  string concatenation of a forward-slash argument and a backslash literal
  (`moduleRoot + "\\target\\classes"`, mixing `/` and `\` in one string) is
  accepted without error but makes the mutation pre-scan silently find zero
  units - PIT's classpath-matching does not normalize separators, and no
  error is raised.

Evidence: `validation/runs/pit-spike/entrypoint-poc/`. Not yet exercised
against a Gradle repo (junit-framework, M2 Faz 2b) - Maven's `dependency:
build-classpath` supplied the runtime classpath here; Gradle's equivalent
extraction is unverified.

**D-52 · Multi-module L2 needs only a classpath/source-dir union; the
first real non-determinism found is in a test's own reflection use, not
PIT** (2026-08-25)
M2 Faz 2b-2e ran on `dropwizard-util` + `dropwizard-validation` merged into
one `ReportOptions` (D-51's `EntryPoint`, extended: two modules' `target/
classes`, `target/test-classes`, source dirs, and dependency classpaths
concatenated). Confirmed real: the coverage-phase output contained classes
genuinely from both modules (18 from `util`, 43 from `validation`), and the
target repo's `pom.xml` files were untouched (`git status` empty). PIT has
no concept of a Maven module - a multi-module target is exactly "give it
the union," no special handling needed on coverdict's or PIT's side beyond
building that union from coverdict's existing `--module` bindings.

2c (determinism) found its first real gap here: 2240/2241 records were
byte-identical across two independent runs (mean Jaccard 0.9998), one
mismatched. Root-caused, not hand-waved: `SelfValidatingValidatorTest.
getMethod()` (dropwizard's own test code, not coverdict's or PIT's) iterates
`ResolvedTypeWithMembers.getMemberMethods()`, backed by JDK reflection with
no ordering guarantee; the shared `hasSignature()` helper's guard line is
hit for whichever candidate methods get checked before a match, and that
candidate order varies run to run. This is exactly the class of instability
the original (dropped, D-51-adjacent) shuffled-test-order gate was meant to
surface - caught instead by plain determinism, without needing PIT to
support test-order control at all. Recorded as a real, bounded (0.045% of
records in this corpus) source of instability that is not a coverdict or
PIT defect: a real user's own reflection-based test fixtures can carry the
same risk, worth a documented product limitation later (M4), not a blocker
now.

2d (cross-engine, JaCoCo) and 2e (ablation) both passed cleanly (0%
mismatch on 416 comparable records; 3/3 sampled multiplicity=1 claims
verified causally). Evidence: `validation/runs/pit-spike/dropwizard/`.

**D-53 · junit-framework (Gradle) confirms D-51's calling model, then hits
a real PIT/ASM bytecode-version ceiling - cut for this repo's shape, not
a mechanism failure** (2026-08-25)
Two things were tested separately and should not be conflated.

**The calling model held.** No Gradle equivalent of `mvn dependency:
build-classpath` exists, so a `--init-script` (external to the repo,
never touches `build.gradle.kts`) was written to dump the test runtime
classpath. Two Gradle-specific obstacles, both solved without touching the
target repo: (1) Isolated Projects (enabled in this repo) rejects
`allprojects{}`/`subprojects{}` cross-project access from init scripts -
solved with `gradle.beforeProject { }` plus that project's own
`afterEvaluate { }`; (2) Configuration Cache (mandatory once Isolated
Projects is on, cannot be disabled) rejects capturing `Project`/
`extensions` inside `doLast` - solved by capturing the `FileCollection` at
configuration time and only carrying that reference into execution.
`git status` stayed empty throughout, extending D-51's "zero target-repo
build-file changes" claim to Gradle.

**The coverage phase did not run.** `junit-vintage-engine`'s main source
set targets Java 7 (major version 51, the library's own compatibility
policy), but its test and testFixtures source sets carry no `--release`
constraint and compile at whatever JDK the Gradle daemon uses (major
version 69, Java 25, on this machine) - the same class-file-version
ceiling Faz 0 hit and fixed in coverdict's own build (`windows-dev-
environment` memory note), except this time in code coverdict does not
control. A repo-external fix was attempted (recompiling test +
testFixtures sources with our own `javac --release 21`, reading but never
writing into the target repo) and worked for the module's own
testFixtures, but the test sources also depend on sibling modules'
testFixtures (`junit-platform-commons` at minimum) carrying the identical
JDK25 problem - each fix uncovered another module needing the same
treatment. Stopped there rather than chasing a cascading recompile of the
repo's test infrastructure.

This is the kill criterion working as designed, not a spike failure: L2
via PIT 1.15.8 cannot currently analyze a repo whose test/testFixtures
bytecode exceeds what PIT's bundled ASM reads, and junit-framework's test
infrastructure does, on this toolchain. Not necessarily permanent - a
future PIT release with newer ASM, or a general (not per-module)
recompile-to-match-main-release mechanism in coverdict itself, could lift
it - but out of M2's scope. Evidence: `validation/runs/pit-spike/
junit-framework/`.

**D-54 · PIT 1.25.9 lifts D-53's ASM ceiling but hits a new, unresolved
zero-block failure under it - staying on 1.15.8** (2026-08-25)
`org.pitest.reloc.asm.Opcodes` confirms the ASM bump is real: 1.15.8 tops
out at `V22=66`, 1.25.9 adds `V25=69` through `V27=71`. But driving 1.25.9's
coverage phase under JDK 25 (this session's ad hoc probe, not a corpus
repo) produced `totalBlocks=0 classesSeen=0` - the agent ran, discovered
tests, and returned nothing. Not reproduced under JDK 17 with JDK 17
bytecode (coverdict's own shape); the failure is specific to the
JDK25-driver combination D-53 needed. 1.25.9 also breaks API compatibility
coverdict would depend on: `ReportOptions.setUseClasspathJar()`/
`useClasspathJar()` is removed outright, and `DefaultCoverageGenerator`'s
constructor gains a mandatory `TestStatListener` parameter - a real,
verified cost independent of the zero-block finding. D-53's "a future PIT
release could lift this" is narrowed: 1.25.9 is that release for the ASM
ceiling specifically, but introduces its own blocker, so M2's L2 stays
pinned to 1.15.8 (`pitest.version` in the parent pom) until both are
resolved. Not corpus-tested; a repo-level verification is separate work.

**D-55 · L2's CLI wiring calls `EntryPoint.execute()`, not
`DefaultCoverageGenerator` directly - the coverage-phase bypass D-47/D-51
implied does not work** (2026-08-25)
A direct call (`DefaultCoverageGenerator.calculateCoverage()`, reading
`CoverageData.createCoverage()` off the return value, skipping
`CoverageExporterFactory` entirely) is API-correct by PIT's own bytecode
and was expected to sidestep the mutation-phase hang note 2 of the CLI-
wiring plan flagged. Empirically it does not: the coverage minion PIT
spawns exits immediately with `UNKNOWN_ERROR` and zero captured output,
root cause not isolated despite reproducing `EntryPoint`'s exact agent-
creation sequence by hand. `EntryPoint.execute()` - D-51's already-proven
calling model - works when two things are added beyond what the spike
needed: `ReportOptions.setMutators(List.of("DEFAULTS"))` (a narrow set like
`NULL_RETURNS` too often finds zero mutable points on a diff-scoped
target, and PIT skips the coverage phase entirely when its mutation
pre-scan finds nothing - confirmed by reproducing exactly that skip), and
`ReportOptions.setExportLineCoverage(true)` (silently gates whether
`CoverageExporter.recordCoverage()` runs at all - its absence was the
actual reason an early attempt produced no output, not a wiring bug).

The real fix for note 2's hang is process-level, not API-level:
`PerTestRunner` spawns a dedicated `PerTestDriver` subprocess, polls for
`CoverdictLineExporter`'s output file (written the moment `recordCoverage()`
fires, before any mutation work starts), and force-destroys the process the
moment that file appears or a 120s timeout elapses - whichever first. This
also resolves D-47's "coverdict must ship a small jar on PIT's tool
classpath": since `EntryPoint` is called in-process by `PerTestDriver`
(itself launched from `coverdict.jar`), the exporter is compiled directly
into `coverdict-cli` and registered via `META-INF/services/
org.pitest.coverage.CoverageExporterFactory` in the shaded jar - no
separate artifact. Verified end-to-end against coverdict's own repo
(`RepoPaths.java`, 3 real per-test entries, `9.8`s wall time including a
full 214-class test-suite discovery/execution PIT itself performs).

Shading requires `maven-shade-plugin`'s `ServicesResourceTransformer`
(`coverdict-cli/pom.xml`): `pitest`'s own JUnit4 `TestPluginFactory`
registration and `pitest-junit5-plugin`'s JUnit5 one collide by filename in
`META-INF/services`, and the default shade behavior keeps only one -
verified by unzipping the built jar and confirming both survive.

The minion PIT spawns needs `org.pitest.coverage.execute.CoverageMinion`
and the exporter class on its own `-cp` - built from `ReportOptions.
getClassPathElements()`, not the driver JVM's classpath - so `PerTestRunner`
appends coverdict's own jar location (self-located via
`getProtectionDomain().getCodeSource().getLocation()`) to the classpath
file it writes for the module under analysis.

**D-56 · L3's mutator set is gregor `RETURNS`+`VOID_METHOD_CALLS`, never
Descartes - and `setFullMutationMatrix(true)` forces `"XML"` into
`outputFormats`** (2026-08-25)
Descartes (LGPL-3.0) can never be a dependency (hard rule 9/D-09/D-20), so
M5 approximates its extreme mutation with the closest Apache-2.0 gregor
mutators PIT ships: `RETURNS` (`EMPTY_RETURNS`, `FALSE_RETURNS`,
`NULL_RETURNS`, `PRIMITIVE_RETURNS`, `TRUE_RETURNS` - confirmed via `javap`
on `ReturnsMutatorGroup`) and `VOID_METHOD_CALLS`. Two known gaps versus
real extreme mutation, documented rather than hidden: gregor's `RETURNS`
mutators only replace the return *value*, not the method body, so side
effects before a `return` still execute where Descartes would remove them
entirely; and no gregor mutator approximates extreme mutation for a `void`
method at all (`VOID_METHOD_CALLS` only removes void calls made *from
within* the mutated method). `PSEUDO_TESTED_METHOD`'s confidence reflects
this: `HIGH` only when every surviving mutant is `RETURNS`-family, `MEDIUM`
when `VOID_METHOD_CALLS` is mixed in.

`javap` on `EntryPoint.checkMatrixMode` confirms `setFullMutationMatrix(true)`
throws `PitError("Full mutation matrix is only supported in the output
format XML.")` unless `"XML"` is in `outputFormats` - not optional, and the
resulting `mutations.xml` lands in the private per-run temp directory and
is never read (`CoverdictMutationListener`, registered under the second,
real output-format name, is what's actually consumed). Listener
activation is two-gated: `javap` on `SettingsFactory.findListeners()`
shows the SPI-discovered listener set is filtered by output-format *name*
before `provides()` is even consulted, so `CoverdictMutationListener.name()`
must appear in `outputFormats` - its `provides()` is deliberately left at
the interface's default `LEGACY_MODE` feature rather than a custom one.

Unlike L2's `PerTestModuleEvidence` (whose `BlockLineResolver` warnings
never reach the parent process - a gap found while building this),
`MutationModuleEvidence`'s wire format carries its own `warnings` list, so
a `MUTATION_TRUNCATED` past the 200k-mutant cap is visible to `--mutation-
report`'s warnings the same way `PER_TEST_TRUNCATED` should be but isn't.

**D-57 · M2 spike's `MINION_DIED` cascade was the spike harness's own
missing `commons-text`, not an L2/L3 mechanism defect** (2026-08-25)
`FINDINGS.md` §7/§8 recorded the cascade's cause as an open guess
(`useClasspathJar` or a temp-jar-path issue). Re-reading the raw logs
while planning M5 finds the actual trigger at `validation/runs/pit-
spike/entrypoint-poc/pit-run.log:345`: `NoClassDefFoundError: org/apache/
commons/text/StringEscapeUtils` inside `XMLReportListener.clean`, thrown
from the spike's own hand-built driver `main` thread. That thread's death
triggers a shutdown hook that deletes the temp instrumentation-agent jar;
every subsequent minion PIT spawns then fails to attach with `agent
library failed to init: instrument`, repeating every ~15s until the log
ends - a cascade, not a root cause, and specific to the spike's
classpath, which never included `commons-text` (a `pitest-entry` compile
dependency the spike never declared). `coverdict.jar`'s shaded classpath
carries it correctly (confirmed: `unzip -l` shows `org/apache/commons/
text/StringEscapeUtils.class` present). Not corrected as a silent edit to
FINDINGS.md's original text (docs conventions: a reversal gets a new
entry, the original stays in git history) - this entry is that
correction, and a pointer was added at FINDINGS.md §7/§8.

**D-58 · `SubprocessWorkspace`'s driver classpath must be the current
JVM's own `java.class.path`, launched via a backslash-normalized
`@argfile`, not a single self-located class's origin passed as a literal
`-cp`** (2026-08-25)
The L2/L3 subprocess launch (originally D-55's design, `PerTestRunner`/
`MutationRunner`) resolved the child driver's own `-cp` by self-locating
one class's protection domain. That is `coverdict.jar` in production -
correct, since D-55 shaded PIT into it - but resolves to a bare `target/
classes` directory with no PIT jars on it when running under Maven/
Surefire, since PIT stays an unshaded separate dependency there. First
found running `MutationRunnerIT` for real (the first time either runner
was ever exercised end-to-end against a live PIT run, not just the
`--no-vcs` rejection paths `AnalyzeCommandTest` covers): `NoClassDefFoundError:
org.pitest.mutationtest.config.ReportOptions`. Fixed by using `java.class.
path` instead (`SubprocessWorkspace.ownRuntimeClasspathEntries()`) - it
collapses to the same single shaded jar in production and correctly
carries PIT's separate jars everywhere else.

That classpath is long enough to need a Java `@argfile` rather than a
literal `-cp` argument (Windows' ~8191-char command-line limit - the same
reason `ReportOptions`' own classpath input goes through a file, D-51).
A second bug surfaced immediately: a quoted `@argfile` token
(`-cp "C:\Users\...`) containing a real Windows path silently
mis-tokenizes - backslash is special inside a quoted `@argfile` token -
and the child fails with `ClassNotFoundException` for its own main class,
not even PIT's. Reproduced in isolation (`java @argfile Hi` with a
backslash path fails; the identical path with `/` instead succeeds).
Fixed by normalizing every classpath entry to `/` before quoting - the
JVM accepts `/` in classpath entries on Windows exactly like `\`.

**D-59 · A real `--mutation-report` run's process count grows fast and
unpredictably in at least one dogfood environment - root cause not
isolated, budget lowered as a safety margin instead of a fix**
(2026-08-25, root-caused and fixed same-day by D-63)
Running `MutationRunnerIT` against coverdict's own repo (a single diff-
scoped target, `setFullMutationMatrix(true)`, `numberOfThreads=1`) after
D-58's fixes: process count climbed past 60 `java.exe` processes within
roughly two minutes, consuming multiple GB and twice bringing free system
memory below 2GB. Isolated manual reproduction attempts (the identical
`MutationDriver` invocation via a hand-built `ProcessBuilder`, same
classpath, same target, `Verbosity.VERBOSE`) consistently found `0`
mutation test units and exited cleanly in under a second - the storm
reproduces only when driven through the real `MutationRunner` code path
from inside a Surefire-forked test JVM, which live debugging in this
session could not further isolate without repeated, escalating risk to
the development machine (three near-OOM incidents during this
investigation, each recovered by killing every `java.exe` process this
session had spawned). Full-matrix mode's higher cost (every mutant must
be tested against its complete covering-test set, not stopped at the
first kill) and PIT's own per-unit minion spawn behavior at
`numberOfThreads=1` are both plausible contributors, neither confirmed.

`SubprocessWorkspace.destroyProcessTree` (D-58 sibling fix: `taskkill /F
/T` on Windows before `destroyForcibly()`, since a killed immediate child
never took PIT's own grandchild minions with it) verified to return
process count to baseline every time a run was allowed to reach its own
timeout or complete naturally, across every observed instance. That
makes the failure mode bounded rather than open-ended, which is why this
ships rather than blocking M5 entirely - but bounded still means "up to
however large the count gets before the budget fires." `MutationRunner.
DEFAULT_BUDGET` and `--mutation-timeout`'s default both drop from 15 to 5
minutes as a direct consequence: erring toward failing fast and cleaning
up a smaller mess, not toward a generous window this session could not
prove is safe. Root-causing the spawn rate itself (candidates: an
explicit `ReportOptions.setMutationUnitSize` instead of PIT's auto
sizing, or profiling one real run with `Verbosity.VERBOSE` end-to-end
rather than killed mid-flight) is unresolved follow-up work, not done
here.

**D-60 · O-05 resolved: coverdict builds its own diff-scoped mutation
target mapping, ArcMutate rejected** (2026-08-25)
`ChangedClassTargets.globsFor` (originally `PerTestCollector`'s private
`targetClassGlobs`, extracted for reuse) already does the git-diff-to-
FQCN-glob mapping D-12 flagged as unresolved: mapped changed `.java`
files under a module's declared source roots become `<FQCN>*` globs fed
to `ReportOptions.setTargetClasses`. No new mechanism needed for M5 - the
same mapping that scopes L2's `--per-test-report` targets scopes L3's.
ArcMutate stays rejected: it is a commercial dependency D-02's "verdict
layer over engines, never own engines, but the engines stay open where
we can reach them" stance does not need, and coverdict's own mapping was
already proven working before M5 started.

One limit carries forward undiminished: `setTargetTests` stays
`Glob.toGlobPredicates(List.of("*"))` - hedges are diff-scoped, the *test
suite PIT runs to find them* is not. A diff-scoped mutation run still
discovers and executes every test that might cover a changed class,
exactly like L2 already does. "Diff-scoped mutation" bounds mutant
generation, not test-execution time - the same limit D-12 first named,
now resolved as designed rather than left open.

**D-61 · M4 reframed: `SUBSUMED_TEST` (mutation kill-set subsumption)
supersedes `COVERAGE_EQUIVALENT_CANDIDATE`/D-46's three-stage gate**
(2026-08-25)
D-46 defined M4 around a symmetric equivalence claim requiring three
independent layers to agree on the same test pair: L2 coverage-overlap
Jaccard, L0 assertion-structure match, and L3 matching mutant-kill sets -
no finding without all three. Reviewed and rejected on two grounds. First,
a three-way exact-match gate fires almost exclusively on near-literal
copy-paste tests, a case `SonarQube` CPD already finds for free on this
same self-scanned codebase (see M1a) - the expensive three-engine motor
was being built for a case a cheaper tool already covers. Second, and more
fundamentally, the L0 assertion-structure gate was cutting the one finding
shape coverdict's other evidence *cannot* get elsewhere: two tests that
look textually different but exercise identical behavior. Requiring L0
agreement discards exactly that signal, leaving only the copy-paste case
Sonar already has - the "extra evidence layer" was actually a narrowing
filter on the one useful result.

Replacement: **`SUBSUMED_TEST`**, a directional, single-evidence-source
claim over L3 mutant-kill sets alone (`Mutant.killingTests`, already
populated by `setFullMutationMatrix(true)` per M5/D-56 specifically for
this milestone). Not "are A and B equivalent" (symmetric, rare, needs
corroboration) but "does test A kill any mutant that no other test also
kills" (directional, a plain fact about the kill matrix, computed in one
pass): `dominators(t) = (⋂_{m ∈ kills(t)} killingTests(m)) \ {t}`, filtered
to `|kills(u)| > |kills(t)|`. This is the strict-subset condition `kills(t)
⊂ kills(u)` restated as a single intersection - O(matrix size), no
pairwise Jaccard loop, no L0/L2 dependency to gate on. `kills(t) = ∅`
(nothing killed) and essential tests (some mutant killed by `t` alone) are
both excluded before any dominator search runs - the former because an
empty set is a subset of every set and would otherwise report every
untested-by-mutation test as "subsumed" by everything, the latter because
it is mathematically impossible for an essential test to be dominated.

L0 and L2 are demoted from required gates to optional message
enrichment - if `--per-test-report`/oracle signatures are available, the
finding message notes whether the dominator's assertions look
textually identical (copy-paste signal) or structurally different
(the genuinely interesting case), but neither presence nor absence
changes whether the rule fires. Only `--mutation-report` is required;
M4 no longer needs `--per-test-report` at all as a precondition.

Known limit carried into the rule doc, not hidden: D-56's `RETURNS`/
`VOID_METHOD_CALLS`-only mutator set is narrow, so subsumption measured
against it likely overstates real redundancy - a richer mutator set would
show fewer tests as subsumed. The finding is phrased as "subsumed under
the mutators this run exercised," never as an unqualified redundancy
verdict, and never suggests deletion - D-46's equivalence-partitioned-
parameterization caution (identical fingerprints can still be genuinely
distinct tests) carries forward unchanged into the new framing.

**D-62 · D-59 watchdog spike: narrow-scoped `-Pmutation-it` stays well
inside safe memory/process bounds; the wide-scope growth pattern is not
reproduced, root cause still open** (2026-08-25)
Before building anything that depends on live `--mutation-report` runs
(M4's `SUBSUMED_TEST`), D-59's unconfirmed process-growth risk needed a
safety net before any further live reproduction attempt, given three
near-OOM incidents in the session that first observed it.
`validation/scripts/mutation-watchdog.ps1` (new) wraps a command, samples
every descendant `java.exe` process's count and summed working set once a
second via `Get-CimInstance Win32_Process` parent-chain walking, and
force-kills the entire subtree the instant either threshold is crossed -
external to and independent of `SubprocessWorkspace.destroyProcessTree`'s
own after-the-budget cleanup, not a replacement for it.

One premise correction first: `destroyProcessTree` already reaches PIT's
full minion tree via `taskkill /F /T`, added specifically because of the
80+-orphan finding that produced D-58/D-59 - the open question is not "why
doesn't cleanup reach every process" (it does), it is "why does live
process count grow so fast in the first place, before any budget or
cleanup logic ever runs."

**Correction, same session, found live:** the first three watchdog runs of
`mvn -Pmutation-it -pl coverdict-cli test` all reported "peak 1 process,
~260MB, clean 90s timeout" - and were wrong. The watchdog's own process
discovery filtered `Get-CimInstance Win32_Process` to `Name = 'java.exe'`
before walking parent links; a fourth run, watched directly in Task
Manager by the user rather than through the watchdog's own log, showed the
real shape of what D-59 first observed: 44 processes and 18+GB, climbing,
while the watchdog simultaneously reported "count=1" the entire time. The
narrow `ChangedClassTargets*` target does **not** make this scenario safe -
it is exactly as exposed as the original D-59 finding, non-deterministic
between runs (three prior watchdog-wrapped runs this same session did not
trigger it; the fourth did), and the false "safe" reading above was a
monitoring-tool defect, not a real result. Root cause of the filter's
blindness was not fully pinned down (a `javaw.exe`-named or otherwise
differently-named hop breaking the java.exe-only parent chain at some
point is the leading theory) and, per the same reasoning that closed the
rest of this investigation below, was not chased further - the fix (track
every descendant process regardless of name, no filter at all) removes
the blind spot without needing to explain it.

With that fix, a fifth run reproduced D-59 again and this time the
watchdog caught it correctly: 3 seconds into `MutationRunnerIT`'s actual
mutation phase, descendant count went 4 -> 8 -> 10 and working set 305MB ->
634MB -> 1.19GB; the watchdog force-killed the whole tree the instant the
(deliberately tightened, 10-process) threshold was crossed, at 20.2s
elapsed - well before anything resembling the original 44-process
incident. `SubprocessWorkspace.destroyProcessTree`'s own after-the-budget
cleanup was never even reached this time; the external guard fired first,
which is exactly the division of labor this script exists for.

**Net effect on D-59 and M4 at the time:** D-59 was not closed - if
anything this session hardened the evidence that it was a real,
reproducible-but-nondeterministic risk on this machine, not a one-off.
Superseded a few hours later in the same session by D-63, which finds and
fixes the actual root cause.

**D-63 · D-59 root cause found and fixed: `MutationDriver` was telling PIT
every one of this repo's 716 test classes was a covering-test candidate**
(2026-08-25)
Investigated by cloning PIT's own source (`hcoles/pitest`, near 1.25.9 -
coverdict pins 1.15.8, but the minion-spawn/coverage-gathering machinery in
`MutationTestUnit`/`WorkerFactory`/`Java9Process` is the relevant code path
and matches across versions closely enough to read) and, after two failed
standalone reproduction attempts (missing `pitest`/`pitest-entry` on a
hand-built classpath, then missing `target/test-classes` - both spike-
tooling mistakes, not PIT bugs), by temporarily setting
`MutationDriver`'s `Verbosity.QUIET` to `VERBOSE_NO_SPINNER` and
`MutationRunner`'s `Redirect.DISCARD` to a real log file, then re-running
the actual `-Pmutation-it` IT test under the (by-then-fixed, D-62) watchdog
with a tight process ceiling to capture output before the tree got killed.

The captured log showed the real mechanism directly: **one minion booted
successfully** (only one "Project base directory is null" banner, so this
was never the retry-driven `MutationTestUnit.runTestsInSeperateProcess`
respawn cascade D-57's terminology first suggested) and immediately logged
`Expecting 716 tests classes from parent` - then re-executed the exact
same 19-test class's full coverage-gathering sequence **six times within
one second**. `MutationDriver.main` had set `options.setTargetTests(Glob.
toGlobPredicates(List.of("*")))` unconditionally since D-56/M5 shipped -
every one of coverdict's own 716 test classes was declared a covering-test
candidate for every mutation run, regardless of how narrow
`--target-classes` was. Under `setFullMutationMatrix(true)`, PIT gathers
coverage once per target method/mutant group being probed rather than
once per run; with an unscoped candidate set that means (target method
count) full re-executions of the entire 716-class suite - the repeated
identical block is that mechanism caught mid-flight, not a crash loop.
This scales with target-method count independent of how small
`--target-classes` is, which is exactly the "climbs fast, non-
deterministic across otherwise-identical runs" shape D-59/D-62 observed:
a target with more methods needing separate coverage passes explodes
faster and further than one with few.

Fix: `MutationDriver.testGlobsFor` (new) derives one `<package>.*` test
glob per unique package among the resolved target-class globs, replacing
the unscoped `"*"`. Same "test lives beside the class it tests" Maven/
Gradle convention `dev.coverdict.analysis.redundancy.TestLocator` (D-61)
already relies on for path resolution - not coverage-verified (that would
need L2's own JaCoCo data, which this driver does not have), but a correct
massive narrowing for every shape coverdict's own corpus work has
exercised (M1c's four real-repo phases all use the standard same-package
test layout). `MutationRunnerIT` (regression check, tightened) went from
reliably hitting its 90s budget and never once completing - across every
run in this session, including three watchdog-wrapped ones that
mistakenly looked "safe" only because the run never got far enough to
matter - to completing in ~1.1s, twice, back to back. Root cause found,
fix verified, `docs/rules/SUBSUMED_TEST.md` and `docs/ROADMAP.md`'s D-59
references corrected accordingly. Cross-package test coverage of a target
class remains an acknowledged gap (a test in a different package than the
class it covers is not discovered) - a real limitation, not a silent one,
and out of scope for this fix (would need L2's actual coverage data wired
through, not just a naming convention).

**D-64 · PIT subprocesses report progress, and can log verbosely** (2026-08-27)
The WTA dogfood (4-module Maven repo, two rounds) hit three separate L2/L3
problems and could root-cause none of them: `service` exhausted both a 300s
and a 1800s mutation budget, `grpc`'s coverage minion died with
`UNKNOWN_ERROR`, and `app`/`data` returned `entries: []` per-test evidence
with no warning at all. In each case the subprocess output that would have
explained it was discarded (`PerTestRunner` discarded both streams) or cut
to a 20-line failure tail (`MutationRunner`), and nothing reported progress
during runs that last tens of minutes.

Decision: both PIT-driving runners merge their child's streams, drain them
on one thread (`ProcessOutputTail`, extracted from `MutationRunner`'s
private `StderrTail` - the `ClasspathListFile` precedent), and report
progress through `EvidenceDiagnostics`. Progress is always on and goes to
**stderr**, never stdout: stdout carries the text report and the JSON must
stay byte-deterministic (hard rule 7). The per-class counter comes from
`MutationResultListener.handleMutationResult`, which PIT calls once per
mutated class - the only stable per-class hook either driver has - relayed
over the output stream the parent already drains (`ProgressMarker`).
`--diagnostics-dir` additionally tees each module's whole subprocess log to
a file and switches the drivers to `Verbosity.VERBOSE`, the only verbosity
whose `showMinionOutput()` is true and precisely what PIT's own minion-crash
message asks the user to enable. Verbosity is tied to the flag rather than
being separately switchable: verbose output with nowhere to land is just a
slower run.

Why this matters beyond logging: a module sitting at 0/219 classes for its
entire budget is a run that never reached the mutation phase, which is a
different defect from a mutation phase that is merely slow. The dogfood had
no way to tell those apart, so no fix could be designed honestly (hard rule
1, hard rule 6).

Same decision closes three silent returns that made requested-but-absent
evidence look like success (hard rule 3a): a module with no mapped changed
production class now warns (`MUTATION_NO_CHANGED_TARGETS` /
`PER_TEST_NO_CHANGED_TARGETS`) instead of returning silently - WTA's first
run had every module land there, since its base ref *was* `HEAD`; evidence
that comes back structurally valid but carrying zero records now warns
(`MUTATION_EMPTY_EVIDENCE` / `PER_TEST_EMPTY_EVIDENCE`); and
`PerTestRunner` producing no export at all is now a collection failure
carrying the output tail rather than an `Optional.empty()` indistinguishable
from "nothing to instrument". Exit-code semantics are deliberately NOT
changed here - whether partial evidence should stop being `complete`/0 is a
separate decision, and one that should be made once the logs from the next
dogfood round say what is actually failing.

**D-65 · `coverdict doctor` diagnoses a Maven repo, `--fix` may call Maven**
(2026-08-27)
Closes the "L2/L3 classpath UX gap" backlog item. Three of four WTA dogfood
rounds lost time to a setup problem discovered only after `analyze` or a PIT
subprocess had already started - a Maven aggregator mistaken for an
analyzable module, a classpath file silently landing under a duplicated
path because `-pl` changes Maven's own working directory, a `mvn clean`
deleting classpath lists a rerun then failed against with no clue why. New
`doctor` subcommand: read-only by default, walks the reactor from the root
`pom.xml` (StAX, same OWASP XXE posture as `JacocoXmlParser`), and for every
real (non-`packaging=pom`) module checks source/test roots, compiled
output, JaCoCo report presence *and freshness* (older than the newest
`.class` - the exact "report may be older than this diff" condition
`ChangedFileClassifier` only reports after the fact), generated-source
directories outside `src/main/java` (the WTA `MISSING_SOURCE_FILE` noise
source), and L2/L3 classpath lists via the same `ClasspathListFile.load`
`analyze` itself uses - a list with zero code paths is a BLOCKER, not a
pass, because that is exactly the silent-empty-evidence shape D-64 closed
on the collector side. Ends with a copy-pasteable `analyze` invocation
built only from modules that are actually usable.

`--fix` regenerates a missing/broken classpath list via a real `mvn
dependency:build-classpath -Dmdep.outputFile=target/... -Dmdep.includeScope=
test` call (module-relative output path - the corrected form the dogfood
runbook settled on) through a new `MavenClient`, modeled directly on
`GitClient`'s subprocess discipline (argv array, never a shell,
SECURITY-POLICY.md #3) and reusing `ProcessOutputTail` (D-64) for output
draining. This is a deliberate, narrow flex of "verdict layer, never own
engines" (AGENTS.md): confined to this one opt-in command, which only ever
asks Maven a question (never changes what it built), and `analyze` itself
never shells to a build tool.

**D-66 · `coverdict.config.json` can carry module/report/classpath
bindings; `doctor --write-config` generates it** (2026-08-27)
Closes the follow-up D-65 left open. `CoverdictConfig` gains a `modules`
array (`id`, `root`, optional `sourceRoots`/`testRoots`, `report`,
`perTestClasspath`, `mutationClasspath` - the same shape
`--module`/`--report`/`--per-test-classpath`/`--mutation-classpath`
express on the command line), validated by the hand-written `ConfigLoader`
reader exactly like every other config key (D-40: no schema-validator
dependency in the shipped jar) and by the checked-in JSON Schema, kept in
agreement by `ConfigLoaderTest`'s existing accept/reject parity check.

Precedence is all-or-nothing at the module-set level, the same rule
`coverageExclusions` already uses: a single `--module` on the command line
makes `config.modules()` invisible entirely, never partially merged with
it - a user overriding one module via a flag should never have to wonder
whether a different, forgotten config module is silently still in play.
Per-module L2/L3 classpath *within* an already-config-sourced module set
does merge, command line over config, since overriding one module's
classpath without restating every other module's full binding is a
reasonable thing to want (`AnalyzeCommand.mergeConfigThenCli`).

`doctor --write-config` (`ConfigWriter`, hand-written JSON writer - same
D-40 reasoning) writes only modules with no BLOCKER, mirroring the filter
`DoctorReportRenderer`'s suggested command already applies. Verified end
to end against coverdict's own repo: `doctor --write-config` followed by
`analyze --no-vcs --repo .` with zero `--module`/`--report` flags produced
a real, correct coverage number from the generated config alone.

**D-68 · L2's real root cause: `CoverdictLineExporter` read the wrong
classpath; `PerTestDriver`'s targetTests narrowed to match D-63**
(2026-08-27)
Closes the WTA dogfood's last open finding (`app`/`data` L2 returning
`entries: []`). `--diagnostics-dir`'s verbose log (D-64) proved PIT's
minion genuinely gathered real coverage against WTA - "Found 143 tests",
"All 143 tests were executed", real `ActionDAOImpl` log output from real
test execution - so the bug was never in evidence collection, only in
what coverdict did with it afterward.

Root cause, confirmed by disassembling PIT 1.15.8's bytecode (`javap`):
`CoverdictLineExporter.recordCoverage()` called `new
org.pitest.classpath.ClassPathByteArraySource()` (no-arg), which resolves
class bytes through `ClassPath.getClassPathElementsAsFiles()` - the
*running JVM's own* `java.class.path`, not `ReportOptions.classPathElements`.
This code runs in the `PerTestDriver` subprocess, whose own `-cp` is only
coverdict's shaded jar (`PerTestRunner`'s `classpathArgFile` never included
the target module's classes - those go to PIT separately, through
`ReportOptions`). So every `BlockCoverage` PIT's minion sent back
genuinely existed, but `LineMapper.mapLines()` could never find the
target class's bytes to map blocks to lines - `BlockLineResolver`'s own
`resolvedLines == null || resolvedLines.isEmpty()` early return then
silently dropped every block, with no error and no warning, because
nothing was ever wrong with *that* check - the input handed to it already
was. Fix: `PerTestDriver` now publishes the real classpath file path via
a second system property (`CLASSPATH_FILE_PROPERTY`, same channel
`MODULE_ID_PROPERTY` already used - the only way to reach an
SPI-instantiated exporter); `CoverdictLineExporter` reads it and
constructs `ClassPathByteArraySource(ClassPath)` explicitly, falling back
to the no-arg default only when the property is absent (a bare unit test
instantiating the SPI directly).

Fixing this exposed a second, independent gap while writing the first
real-PIT-subprocess test for L2 (`PlaygroundMutationIT`, extended to
assert non-empty `entries`, not just no error): `PerTestDriver` still had
D-59/D-63's unscoped `targetTests=List.of("*")` - never narrowed like
`MutationDriver`'s package-scoped globs. Under
`SubprocessWorkspace.ownRuntimeClasspathEntries()` (this JVM's own
dev/test classpath, appended to every driver's `-cp` unconditionally),
that told PIT every test reachable there - including coverdict's own
`MainTest`/`PlaygroundFunctionalTest` - was a covering-test candidate,
which blew well past the 120s per-test collection timeout running under
`-Pmutation-it`. `MutationDriver`'s `testGlobsFor` (private, package-only
narrowing) is now `TestGlobs.samePackageGlobsFor` in
`dev.coverdict.analysis.subprocess` - shared by both drivers, no behavior
change for `MutationDriver` itself. Real WTA production runs (`java -jar
coverdict.jar`, single shaded jar on `-cp`) were not exposed to the same
failure mode as sharply, since there is no large dev/test suite riding
along - but the narrowing is correct there too, for the same D-63
reasoning.

Verified end to end against a real PIT subprocess, not stubbed:
`PlaygroundMutationIT` now asserts `perTest.modules[0].entries` is
non-empty against the checked-in playground fixture - the exact
regression this class of bug would reintroduce silently otherwise.

**D-69 · D-51's canonicalization rule was documented but never actually
applied - `doctor --fix`'s relative codePaths made both L2 and L3
silently find zero units on a real WTA module** (2026-08-27)
Round 6 of the WTA dogfood: after D-68 closed L2's `entries: []` bug,
`app`/`data` regressed to a different symptom - PIT's own log:
`Created 0 mutation test units in pre scan` / `No mutations found. This
probably means there is an issue with either the supplied classpath or
filters.`, instantly (<1s), for BOTH `--per-test-report` and
`--mutation-report` against `data`'s 13 real DAO classes. Ruled out by
direct evidence before looking at code: the target `.class` files
genuinely exist on disk (`ls` against the real WTA checkout, fresh
timestamps matching a real `mvn clean install`); the classpath file's two
stray `.pom` entries (a `mvn dependency:build-classpath -Dmdep.
includeScope=test` quirk for import-scope BOM dependencies) were a red
herring - stripping them and rerunning produced the identical zero-units
result.

Root-caused by disassembling `pitest-entry:1.15.8`'s
`MutationCoverage.class` with `javap`: `findMutations()` calls
`buildMutationTests(new NoCoverage(), ...)` - `MutationTestBuilder.
createMutationTestUnits(code.getCodeUnderTestNames())` runs entirely
*before* any coverage pass or test execution, confirming the zero count
is a pure static codePaths/classPathElements resolution failure, not a
test-execution problem. That pointed straight back to **D-51's own
already-documented rule**, written the day PIT was first integrated:
"Every path handed to `setClassPathElements`/`setCodePaths`/
`setSourceDirs` must be canonicalized (`File.getCanonicalPath()`)...
makes the mutation pre-scan silently find zero units, no error raised."
Reading `MutationDriver.main()` and `PerTestDriver.main()` confirmed the
rule was never actually implemented anywhere in the codebase - both read
`codePaths`/`classPath` raw from a list file and pass them straight to
`options.setCodePaths(...)`/`setClassPathElements(...)` with zero
transformation. `ClasspathListFile.resolveLine()` (the shared parser
behind `--mutation-classpath`/`--per-test-classpath`/`doctor --fix`) had
the same gap: `repoRoot.resolve(entry).toString()` never collapses `.`/
`./` segments.

This survived undetected because every classpath file used in the dogfood
until now was hand-built or already-absolute (`.m2` jar paths, or a
manually `.toAbsoluteString()`'d module output dir). `doctor --fix`'s
`ClasspathFixer` (D-65) was the first caller to write a module's own
`target/classes`/`target/test-classes` as a plain relative string
(`RepoPaths.join(module.root(), "target/classes")`, no canonicalization
step) - exactly D-51's documented trap, just never exercised by a real
run until this round.

Fixed with a new shared helper, `dev.coverdict.analysis.subprocess.
CanonicalPaths.canonicalize(List<String>)` (`File.getCanonicalPath()`
per entry, falling back to the absolute form on a rare `IOException`
rather than dropping the entry or throwing - hard rule 3a), applied at
three points: `MutationDriver.main()` and `PerTestDriver.main()`
immediately before their `options.set...` calls (the literal call site
D-51 is about, and a guaranteed choke point regardless of how the list
files were produced), and `ClasspathListFile.resolveLine()` (fixes
`--mutation-classpath`/`--per-test-classpath`/`doctor --fix` dedup too -
`./target/classes` and `target/classes` now correctly collapse to one
entry instead of two).

Verified two ways: a new `ClasspathListFileTest` asserts a `./`-prefixed
directory entry resolves to its canonical form and that two
differently-written equivalent entries dedup to one; `PlaygroundMutationIT`
(the real-PIT-subprocess test D-68 added) now deliberately writes the
playground fixture's own `target/classes`/`target/test-classes` as
relative `./`-prefixed entries (the exact shape `doctor --fix` produces)
instead of absolute ones, reproducing the real WTA bug locally - it
passes end to end (4 known L3 findings, non-empty L2 entries) only with
this fix in place. `mvn verify` full green.

**D-70 · `fileCoverage` is an opt-in block, one per-file `MetricSet` recomputed
by the same `MetricsEngine`, never a client-side calculation** (2026-08-27)
Plan.md's M6 (IDE surface) Faz 1: `changedFiles[].uncoveredNewRanges` is
diff-scoped only, but `AnalyzeCommand`'s already-filtered `ResolvedSourceFile`
list (post `ExclusionFilter`, pre-`MetricsEngine.compute`, hard rule 4) is the
single dataset `coverage.overall` is built from - serializing it needs zero
new analysis. `--file-coverage` (opt-in: assertj-scale payload measured at
+~2MB in Plan.md's research) emits `fileCoverage.files[]` (`module`, `path`,
a per-file `MetricSet` computed by the same `MetricsEngine.compute` call
`overall` uses - `Metric.percent()`'s `BigDecimal` half-up rounding is not
float-safely reproducible in TypeScript, so an IDE must never recompute a
percentage itself) and compact `[line, mi, ci, mb, cb]` tuples matching
`LineCoverage`'s own field order, plus `fileCoverage.excluded` (repo-relative
paths `ExclusionFilter` removed) so an IDE can render "excluded" rather than
"unknown"/"uncovered" (hard rule 3a). `ExclusionFilter` gained `partition()`
(returns kept+excluded together); `apply()` now delegates to it. Wired at
all three `VerdictDocument` construction sites that have the filtered dataset
in scope (no-vcs, diff-mode success, diff-mode's D-26 `AnalysisException`
catch) - `incompleteDocument` (pre-binding failure) correctly has none to
give, same as `overall` there. Schema addition only (new `fileCoverage`
$defs, existing goldens unchanged) - `schema/examples/golden-file-coverage.json`
and a third tool-output golden (`fixtures/verdicts/file-coverage.json`,
`VerdictGoldenTest`) added; `validation/SHA256SUMS` updated for both plus a
pre-existing drift found while touching it (`golden-per-test.json`/
`golden-mutation.json` were never added after D-55/D-56, per the c4377d2
precedent D-42 already named).

**D-71 · `--mutation-target` bypasses the diff entirely; explicit targets are
all-or-nothing across the whole run** (2026-08-27)
Plan.md M6 Faz 2: `--mutation-target <id>=<FQCN>` (repeatable) resolves each
class straight against `module.sourceRoots()` via the new `SourceRootClassIndex`
(same repo-escape guard as `ModuleBinder.resolvePath`, unresolved -> excluded
rather than joined with a source root), never through `ChangedClassTargets`/a
diff - which is what lets it work under bare `--no-vcs` (`validateMutationReport`
now only rejects `--mutation-report` + `--no-vcs` when no target was given).
Given at least one target, every module's diff-derived targets are ignored
entirely (D-66's config-modules precedent), including modules with no target
of their own - `MutationTargetResolver` warns each such module
(`MUTATION_TARGET_NOT_BOUND`) or each unresolved class
(`MUTATION_TARGET_UNRESOLVED`, hard rule 3a) with a single, specific reason -
`MutationCollector.collectForTargets`'s own empty-target skip adds nothing
further, to avoid a second, less precise warning for the same fact.
`MutationRuleEngine`/`MutationCollector` both gained an injectable overload
(target-mode `classNameToPathByModuleId`/`targetGlobsById`) alongside their
existing changed-files-based one, which callers (including existing unit
tests) keep using unchanged. Verified end to end with a real PIT subprocess
against coverdict-playground (`PlaygroundMutationIT`, `-Pmutation-it`): a
class named by `--mutation-target` under `--no-vcs`, with no git repository
at all, produces a real, path-resolved `PSEUDO_TESTED_METHOD` finding -
Plan.md Faz 2's own completion criterion. Checked, not assumed, per the
plan's own open item: `RedundancyRuleEngine`/`TestLocator` carry no
changed-files dependency at all (`SubsumedTestRule`'s test-path resolution
already reads a module's declared test roots straight off disk) - nothing
to fix there.

**D-72 · The agent skill lives in `skills/`, renders JSON, and never authors a
finding** (2026-08-29)
Hard rule 7 already names **skill** as a rendering surface of the same JSON, and
ROADMAP's backlog carried the one-liner - this promotes that item, it is not new
scope. The skill ships at `skills/coverdict/` (repo root), **not** under
`.claude/`: hard rule 7 makes it a product surface that versions with the schema,
the same reasoning that puts `schema/` and `docs/rules/` at the root, whereas
`.claude/` holds config for agents working *on* coverdict - the `AGENTS.md`
audience. Conflating the two would auto-load a consumer skill into maintainer
sessions. Installation is a copy into the host tool's own skills directory; a
`coverdict skill install` subcommand is backlog, not this pass (hard rule 8).
The skill is bound by the same hard rules as every other surface: it renders
verdict JSON and **may never generate, infer, or store a finding of its own**
(hard rule 1), never suggests deletion at any confidence (hard rule 3), and never
reports an exit-3 run as success (hard rule 3a). It also owns the loop's evidence
capture (`reference/loop-log-template.md`), so the ROADMAP kill criterion becomes
measurable on every future run rather than only when someone remembers to record
one - six WTA dogfood rounds produced zero such evidence precisely because
nothing required it.

## Rejected

**R-01 · LLM-as-judge for verdicts** — non-deterministic, costs per run, not
reproducible in CI; also the crowded, undifferentiated corner of the market.
**R-02 · Building on OpenClover for per-test coverage** — source
instrumentation, experimental Java 17 support, no parallel execution.
**R-03 · Python core** — see D-03.

## Open

**O-02 · SARIF as an additional output format** — would give GitHub code
scanning and IDE problem-panel integration nearly free. Evaluate with CI work.
**O-07 · Kotlin/Android as a supported target** — D-10 (JavaParser) does not
extend to Kotlin syntax; would need its own AST/symbol-resolution frontend.
JaCoCo reads Kotlin bytecode fine, but inline functions copy code to call
sites and break line attribution; Android adds its own report layout,
flavor/variant matrix, and generated sources. Out of scope unless a dogfood
repo (M0 item 1) forces it.

Resolved: O-01 is **coverdict**; O-03 is D-13/D-18; O-04 is D-23; O-05 is
D-60; O-06 is D-04.
