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
