# Roadmap

Only the current milestone is specified in detail. Later milestones are
sketches and are replanned at each gate.

Levels used throughout the project:

- **L0 — static oracle evidence:** conservative source analysis; no runtime
  claim about what a test executed.
- **L1 — aggregate coverage:** JaCoCo XML plus git diff; no per-test linkage.
- **L2 — per-test execution fingerprints:** opt-in analysis for redundancy
  candidates, subject to the attribution limits in D-18.
- **L3 — mutation evidence:** method-centric PIT/Descartes findings; a surviving
  mutant does not by itself prove that an individual test verifies nothing.

## M0 — Product and contract readiness (complete, 2026-08-23)

Production analyzer work was a no-go until this gate closed (D-14). All seven
deliverables are checked in; M1 is now the current milestone.

Deliverables:

1. Name one primary persona and one canonical pre-push workflow. Record three
   real dogfood repositories, their build tools, current workaround, and setup
   tolerance. Resolve O-04 from this evidence.
   **Done** (2026-08-23): `docs/M0-PERSONA.md` — AI-agent-loop persona, three
   public Maven proxies (no in-house repos available), O-04 → D-23.
2. Specify the CLI input model: repository, an explicit base-branch diff mode
   *and* an uncommitted working-tree diff mode (both first-class, not
   either/or), the no-`.git`/no-VCS fallback (overall coverage only; new-code
   coverage fails closed per hard rule 3a), report to module binding,
   main/test source roots, language level, classpath, exclusions, and
   supported/unsupported input matrix.
   **Done** (2026-08-23): `docs/M0-CLI-INPUT.md`.
3. Check in a draft JSON Schema and golden examples containing schema/tool
   versions, evidence provenance, analysis status, module/source identity,
   named metric numerators and denominators, findings, warnings, and stable
   deterministic ordering. Paths are normalized repo-relative paths.
   **Done** (2026-08-23): `schema/coverdict-verdict.schema.json` + three
   goldens; `schema/validate-goldens.py` (discardable spike) passes and six
   invalid variants are rejected.
4. Specify every L0 rule with positive, negative, and unresolved fixtures;
   recognized oracle APIs, custom-oracle configuration, severity, confidence,
   suppression, and stable finding fingerprints.
   **Done** (2026-08-23): `docs/rules/` (shared contract + 4 rule specs),
   `fixtures/rules/` (3 fixtures per rule).
5. Check in a reproducible validation manifest: repository commit, JDK, build
   command, report command, fixture hashes, labeling protocol, benchmark machine,
   and cold/warm measurement commands.
   **Done** (2026-08-23): `docs/M0-VALIDATION-MANIFEST.md` +
   `validation/SHA256SUMS`.
6. Write the untrusted-input policy: secure XML parsing, resource limits, safe
   process invocation, output escaping/path handling, and no network or telemetry
   by default.
   **Done** (2026-08-23): `docs/SECURITY-POLICY.md`.
7. Define the release contract: Apache-2.0 `LICENSE`, generated `NOTICE` and
   dependency inventory, distribution channel, checksums, and support window.
   **Done** (2026-08-23): `LICENSE` + `docs/RELEASE-CONTRACT.md`. The contract
   was only prose until 2026-08-25: `mvn -P release clean package` now really
   generates NOTICE, the transitive inventory and a verifiable SHA-256SUMS,
   with the hard-rule-9 LGPL gate proven to fail the build (D-44).

M0 is done only when all seven artifacts are reviewable and no required product or
input decision remains implicit. Contract spikes may be discarded; they are not
the production foundation (D-03).

## M1 — Trustworthy CLI wedge (current, v0.1)

M1 is L0 + L1 only. It is deliberately described as changed-code coverage plus
a conservative static oracle critic, not as a measurement of “real coverage”
(D-15).

### M1a — Evidence-safe diff coverage

Skeleton done (2026-08-23): Maven multi-module build on Java 17, picocli CLI,
exit-code contract wired and tested, self-scan green.

**`--no-vcs` vertical slice done (2026-08-23):** `analyze --no-vcs` runs
end to end - JaCoCo XML parsing (secure StAX, D-16 duplicate-class
rejection), module/source-root binding with on-disk verification, the D-05
single-exclusion-layer, all three metric modes, and schema-valid JSON/text
output, all real (not stubbed). Verified against coverdict's own real
`jacoco.xml`: `jacoco-line` matches JaCoCo's report-level LINE counter
exactly (518/549, D-04 parity), two runs are byte-identical.

**`--base`/`--uncommitted` diff modes done (2026-08-24):** `analyze --base
<ref>` and `analyze --uncommitted` run end to end - `GitClient` invokes git
as an argv array (no shell, D-26), `UnifiedDiffParser` reads `--unified=0`
hunk headers, `ChangedFileClassifier` sorts every changed `.java`/`.kt`/
`.scala` path into mapped/excluded/non-executable/unsupported/unknown
(D-27), and `coverage.newCode` is a real `MetricSet` computed by the same
`MetricsEngine.compute` as `overall`, over a line-restricted projection
(hard rule 4). Verified against coverdict's own history: the
`sum(changedFiles[mapped].newLines) == newCode.denominator` invariant holds
on a real run, changed test files classify `excluded` (not incomplete) as
designed, and a genuinely untracked Java file correctly produces
`UNTRACKED_JAVA_FILE` + exit 3 while still preserving the already-computed
overall coverage in the same document (D-26). Missing refs, missing merge
base, and unmapped/absent-from-report changed Java files all produce a
structured incomplete result with the specific failing prerequisite named,
never a false green. M1a is now feature-complete; M1c will still need to
measure `sonar-compatible` new-code parity against the real SonarQube UI
(criterion 2) once the validation corpus work starts.

- Standalone HTML is deferred until dogfood proves a need.
- coverdict's own codebase is scanned by a local, self-hosted SonarQube
  (Docker, `localhost:9001`; token via `SONAR_TOKEN` env var, never
  committed) as coverdict's own quality gate — run via
  `mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar ...`
  (`mvn sonar:sonar` fails, plugin prefix isn't registered); currently 0 open
  issues, ~87% real line coverage (2026-08-24, after M1b). Separate from the `sonar-compatible`
  parity ground truth mentioned in the
  kill criteria below, which targets the M1c validation corpus, not our code.

### M1b — Conservative static oracle critic

**Engine + four rules done (2026-08-24):** `analyze` now runs real L0 oracle
detection over test sources - JavaParser AST + Symbol Solver
(`dev.coverdict.analysis.oracle`), a two-tier call-resolution scheme (D-28:
Symbol Solver first, then import-anchoring so a run with no `--classpath`
still resolves the JUnit/AssertJ/Mockito/Hamcrest allowlist with certainty),
same-compilation-unit private-helper traversal, and all four rules
(`NO_RECOGNIZED_ORACLE`, `TAUTOLOGICAL_ORACLE`, `CATCH_ORACLE_WITHOUT_FAIL`,
`NULL_CHECK_ONLY`) implemented exactly to their checked-in specs
(`docs/rules/`). `coverage.newCode` sits alongside a real `findings` array in
the same document; `--findings-scope` (`all`/`changed`, D-28) controls which
test sources are scanned. Verified two ways: a fixture-driven test
(`OracleRuleEngineFixturesTest`) executes every `docs/rules/**` fixture and
asserts its `// expect:` header against the real engine output - the M0 rule
specs are now executable, not aspirational prose - and a real run against
coverdict's own 14 test files / 103 `@Test` methods found zero false
positives while a dedicated negative-test fixture confirms the detector does
fire on a genuinely oracle-less test.

- `--classpath` is implemented (D-39): `<id>=<file>`, one jar path per line,
  built into JavaParser's solver set via `ClasspathLoader`. Unreadable lists
  and unusable entries warn (`CLASSPATH_FILE_UNREADABLE`,
  `CLASSPATH_ENTRY_UNUSABLE`) rather than failing the run - D-17's direction
  holds: a missing classpath degrades resolution, a present one never
  silently upgrades confidence.
- `--config` is implemented (D-40): strict `coverdict.config.json`, schema
  checked in, precedence command line > config > defaults.
- `customOracles` is implemented (D-41), closing the "D-17 territory" gap all
  four corpus phases marked out of scope.
- `suppressions` is implemented (D-42) with a `SUPPRESSED_FINDINGS` warning
  carrying a machine-readable `count`. Baselines and changed-findings-only CI
  gating remain M3 (pre-CI) work, as originally scoped.
- JUnit 4 `org.junit.rules.ExpectedException` and the `@Disabled`/`@Ignore`
  message note are implemented (D-43) - both were specified in M0 with no
  implementation and no recorded deferral, and no corpus phase happened to
  contain either shape.
- Baselines and changed-findings-only CI gating are still open (M3 pre-CI
  work, as originally scoped); rule/path suppression itself shipped in D-42.

### M1c — Hardening and validation

M1 is done when all of these hold on the pinned M0 corpus:

1. Every golden output validates against the JSON Schema and is byte-for-byte
   deterministic across two clean runs.
2. `jacoco-line` equals the JaCoCo LINE counter exactly, and
   `sonar-compatible` matches the same-scope SonarQube UI within ±0.1. Both
   overall and new-code outputs carry the metric id and raw numerator/denominator.
3. Every changed Java path is classified as mapped, excluded, non-executable,
   unsupported, or unknown. There is no silent drop path. Standalone XML evidence
   is labeled freshness-unverified because XML does not prove its source commit.
4. Rule precision is measured per rule and confidence tier: review all findings
   up to 100 per repo/rule, otherwise a seeded deterministic sample of 100.
   Reviewed labels and reasons are checked in; HIGH precision must be at least
   90%. Seeded fixtures also record recall and the inconclusive rate.
5. The required real-repo phases all pass at pinned commits: a small canary,
   AssertJ Core for fluent/custom assertions, JUnit 5 for Gradle and dynamic-test
   source constructs, and Dropwizard for multi-module Maven and mixed JUnit.
   Runtime-event-to-source attribution is explicitly M2/L2 work, not M1.
6. Analyzer-only median and p95 runtime plus peak memory are recorded with the
   exact command, hardware, JDK, XML size, Java LOC, and module count. Test count
   is not used as the performance proxy for an analyzer that does not run tests.
7. Malformed/XXE XML, duplicate module identities, Unicode/space/CRLF paths,
   rename, missing merge base, unsupported Java syntax, and Windows/POSIX path
   normalization have automated negative tests and structured failures.

**M1c-1 (repo-internal hardening: criteria 1 and 7) done (2026-08-24):**
Everything network-free and corpus-free in M1c is closed. Criterion 1:
`schema/examples/` stays the hand-written M0 contract layer; a second,
tool-OUTPUT layer (`fixtures/verdicts/no-vcs.json`, `base-ref.json`) is
compared byte-for-byte against a fresh CLI run every test invocation
(`VerdictGoldenTest`), and the existing diff-mode two-run test now compares
bytes, not strings. Criterion 7: closed every negative-test gap the M1b-era
suite had - `UNPARSEABLE_TEST_SOURCE` (previously untested), the findings cap
and its file-boundary truncation semantics, a rename with real content
modification (previously only 100%-similarity pure renames were tested),
`MISSING_MERGE_BASE` end to end through the CLI (previously unit-level only),
an XXE fixture proven not to read its target file's real content, Unicode/
space paths proven end to end with the file really on disk (previously only
the not-found branch), CRLF line endings in both JaCoCo XML and Java test
sources, and a Windows-backslash `--report` argument proven to normalize to
forward slashes in the JSON. SECURITY-POLICY.md §6 also went the other
direction: three spec'd-but-unimplemented clauses (256 MB report size cap,
repo-root path-escape rejection, terminal control-character escaping) are
now real, each behind the negative test §6's table names. A repeated
`--module`/`--source-roots`/`--test-roots` id is now a rejected invocation
(hard rule 3a) instead of a silent last-one-wins. See D-29.

Still open, all requiring the pinned M1c validation corpus
(`docs/M0-VALIDATION-MANIFEST.md`) and therefore separate steps: criterion 2
(`sonar-compatible` parity against the real SonarQube UI), criterion 4
(rule precision labeling), criterion 5 (the four real-repo phases), and
criterion 6 (analyzer-only benchmark harness - none exists yet).

**M1c-2 phase 1 (gson canary) done (2026-08-24):** reusable harness added
(`validation/scripts/run-corpus-phase.ps1`, `sonar-parity.ps1`,
`benchmark-phase.ps1`, `scaffold_labels.py`) and exercised end to end on
`google/gson` @ pinned commit, real results archived under
`validation/runs/gson/`. See D-30.

- **Criterion 2** (overall scope only): `sonar-compatible` matched the local
  SonarQube instance's `coverage` measure exactly (91.0 vs 91.0, delta 0.0,
  well within ±0.1) - `jacoco-line`/`line_coverage` also matched exactly as
  a bonus cross-check. New-code parity is **not measurable** on this local
  instance: it is Community Edition, which does not support branch/PR
  analysis. Documented limit, not a silent pass (hard rule 3a).
- **Criterion 4:** calibration round 1 of 2 (kill criteria). A 100-item
  seeded sample (seed 42) of `NO_RECOGNIZED_ORACLE` findings scored 4.2%
  HIGH-tier precision (4/96) against the required ≥90%. Root cause
  identified and confirmed on every sampled false positive: gson's test
  suite uses Google Truth (`com.google.common.truth.Truth.assertThat`),
  which is absent from the D-24 recognized-oracle allowlist - both direct
  calls and calls reached through the engine's own same-compilation-unit
  helper traversal (confirmed working correctly). A concrete, scoped fix is
  identified (add Truth to D-24) but not implemented in this session; round
  2 is a re-run of this same sample after that change.
- **Criterion 5:** 1 of 4 real-repo phases run (gson canary). The harness
  is repo-agnostic; phases 2-4 (assertj, junit-framework, dropwizard) are
  parameter changes to the same three scripts, not new code.
- **Criterion 6:** harness exists and produced real numbers on gson - cold
  182.5 ms, warm median 182.5 ms, warm p95 184.5 ms, peak working set
  57.1 MB (Windows 11 Pro 26200, JDK 17.0.17 Temurin, 5515 executable
  lines / 123 test files / 1 module).

**Calibration round 2 (post-D-31 Truth fix) done (2026-08-24):** criterion 4
now closes for the gson canary. Findings dropped from 1090 to 38 (-96.5%);
`NO_RECOGNIZED_ORACLE` HIGH-tier precision is 94.3% (33/35), **meeting the
≥90% bar** - kill-criteria round 2 of 2 passes, the rule stays. See D-32 and
`validation/runs/gson/round2/precision-summary.md`. Two new, narrower gaps
surfaced and are backlogged (not this session): the oracle-helper traversal
only follows **private** same-file helpers, missing real oracles behind a
public static helper two gson tests use; and JavaParser's symbol solver
cannot resolve an overloaded call whose argument is a lambda
(`assertThrowsStackOverflow(() -> ...)`), producing three correctly-hedged
`INCONCLUSIVE` findings rather than a wrong verdict.

**Both backlog gaps closed (2026-08-24):** see D-33. Helper traversal now
follows private-or-static same-file helpers (not private-only); the
`CircularReferenceTest` cause turned out not to be lambda arguments at all
(disproved by a minimal reproduction) but an external/unresolvable
parameter type on the callee's own declaration - fixed with a same-file
name-match fallback for exactly that failure shape. Re-verified on the real
gson checkout: 38 findings drop to 33, all HIGH, all true-positive - 100%
of what was reviewed, up from 94.3%. `validation/runs/gson/round2-followup.md`.

**Phase 2 (assertj) allowlist gap closed (2026-08-24):** see D-34. A
5343-finding run from a separate session (reproducibility gap - its script
changes did not exist in this repo) traced to AssertJ's own `Assertions`/
`BDDAssertions` classes each declaring ~27-29 typed-subject entry points
sharing one prefix, of which D-24 only recognized 4 by exact name - not a
new library, D-24's existing AssertJ entry was incomplete.
`OracleAllowlist.isChainAnchor` now matches both by prefix
(`assertThat`/`then`). Re-verified on the real corpus (same `jacoco.xml`,
no rebuild needed): 5343 to 2148 findings (-59.8%). `validation/scripts/
run-corpus-phase.ps1` gained multi-patch support, git-reset-before-build,
and a `-SkipBuild` mode, making the corpus harness actually reproducible
from the repo again. `validation/runs/assertj/`.

**Phase 2 (assertj) criterion 4 done, calibration round 1 passes
(2026-08-24):** all 102 sampled findings (100 `NO_RECOGNIZED_ORACLE`, seed
42, plus the full 2-item `CATCH_ORACLE_WITHOUT_FAIL` population) reviewed
against real source - **100% true-positive.** Unlike gson, assertj needed
only one round: its remaining findings are overwhelmingly genuine, because
much of assertj's own suite tests its **own** internal assertion engine via
void calls relying on non-throw (no missing-library gap, no second oracle
to find). Two new, low-volume, structurally distinct allowlist gaps
surfaced and are backlogged (not fixed this session): `WithAssertions`
interface delegation, and `InstanceOfAssertFactory` chains (terminal
assertions on a factory-returned assert object, never touching `Assertions`/
`BDDAssertions`). `validation/runs/assertj/precision-summary.md`.

**Phase 2 (assertj) criteria 2 (overall) and 6 done (2026-08-24):** sonar-
compatible parity PASS, exact match (74.4 vs SonarQube's 74.4; jacoco-line/
line_coverage also matched exactly, 77.7 vs 77.7, as a bonus cross-check).
Benchmark: cold 207.2 ms, warm median 175.6 ms, warm p95 199.4 ms, peak
working set 56.9 MB - close to gson's numbers despite a ~4x larger source
tree, since analysis time scales with changed/analyzed lines, not repo
size. See D-35 for a real incident hit while running the Sonar scan (local
machine froze, required a hard restart) and its fix - a WSL2 memory cap,
now a documented prerequisite for any further phase.

**Phase 3 (junit-framework) harness Gradle support + criterion 4 done
(2026-08-24):** `run-corpus-phase.ps1` gained a Gradle branch (D-36);
regression-clean on gson (33) and assertj (2148). Module scoped to
`junit-vintage-engine` - the only Jupiter/platform component with its own
self-contained tests (every other component centralizes tests in a separate
`jupiter-tests`/`platform-tests` module, bound via whole-repo JaCoCo
aggregation - out of scope, see D-36). Precision: HIGH 1/1 (100%, the
manifest's kill-criterion, met trivially at n=1); MEDIUM 0/46 and
INCONCLUSIVE 0/4, both reported in full per the manifest's "no hiding a
failing tier" rule. 82% of all findings trace to one root cause: recognized
libraries have always owned the **root** of a chain (`assertThat(x)`,
`then(x)`); JUnit Platform Testkit's `Events.assertEventsMatchExactly` is
instead the chain's **terminal** link, rooted at the test's own private
helper - `isChainAnchor` never checks anything but the root. A same-session
fix attempt (checking the terminal separately) was implemented,
fixture-verified, and regression-clean - then **reverted** once real-world
testing showed coverdict's real `analyze` path has no classpath mechanism
at all to resolve the terminal's owning type, and the affected files never
literally import it either (only ever an inferred chain-return type) - no
fact-based path remained short of a name-only heuristic or real classpath
support (M2/M3-scale). Backlogged below, not shipped half-verified. The
remaining 8 findings trace to a second, narrower, unfixed gap:
`sameFileNameMatch`'s ambiguity bailout ignores call-site arity when
disambiguating same-file overloads. `validation/runs/junit-framework/
precision-summary.md`. Sonar parity (criterion 2) deferred - `sonar-
parity.ps1` is Maven-only, and junit-framework's Isolated-Projects, 30+
module tree makes an ad hoc Gradle equivalent a real, separate design task,
not a same-session extension (D-36). Benchmark (criterion 6) done, after
fixing a second real pre-existing bug in `benchmark-phase.ps1` (undrained
redirected stdout/stderr deadlocked the child process on this repo's larger
output, D-36): cold 1909.4 ms, warm median 1918.6 ms, warm p95 1937.9 ms,
peak working set 425 MB - notably higher than gson's despite a smaller
analyzed tree, likely repo-size overhead from `--repo` pointing at the
whole 30+-module checkout; not investigated further.

**Phase 4 (dropwizard) done (2026-08-25):** all of criterion 5's four
real-repo phases are now run. Corpus harness gained real multi-module
binding (D-37): `run-corpus-phase.ps1` and `sonar-parity.ps1` both take
module-id/root arrays instead of a single scalar pair, one reactor build
(`mvn -pl modA,modB -am`), one coverdict invocation binding every module's
own `--module`/`--source-roots`/`--test-roots`/`--report`. Modules:
`dropwizard-util` (leaf, no `dropwizard-*` dependency) + `dropwizard-
validation` (depends on `dropwizard-util`) - a real reactor dependency
edge, 105 `@Test` methods total, chosen small to stay clear of the D-35
memory risk. `-LanguageLevel 11` (this repo's `maven.compiler.release`,
not the harness's 17 default) and JaCoCo `0.8.14` (this repo's pinned
version, not the harness's 0.8.13 default). Criterion 4: **0 findings**
across every rule/tier over all 105 tests - both modules are ~1083
`assertThat(...)` (assertj) plus 2 Mockito `verify(...)`, already fully
covered by D-34's allowlist, no `@Disabled`/`@Ignore` methods; a real,
corroborated result (non-trivial coverage numbers prove the scan ran), not
a silent gap - see `validation/runs/dropwizard/precision-summary.md`.
Criterion 2 (overall): **exact match**, 81.1 vs SonarQube's 81.1 (delta 0);
the reactor-scan sonar-parity path worked on the first attempt, no repeat
of the earlier single-module `-pl` + `sonar:sonar` failure (dropwizard's
two modules are real `<module>` entries of the root aggregator, unlike
gson/assertj's module dirs). Criterion 6: cold 4608.4 ms, warm median
4294.5 ms, warm p95 4443.1 ms, peak working set 603 MB - the highest of any
phase despite the smallest analyzed tree, confirming D-36's open question:
`--repo` pointing at the whole 34-module checkout drives overhead
independent of analyzed size. See D-37.

**Manifest correction (D-38):** the phase-4 "forces" column claimed "mixed
JUnit 4+5"; verified false at the pinned commit (`git grep` for JUnit 4
imports/annotations across the whole repo returns nothing, every module
pom excludes `junit:junit`, all 321 `@Test` methods are JUnit 5) -
`release/4.0.x` is fully migrated. Corrected in
`docs/M0-VALIDATION-MANIFEST.md` to "multi-module Maven, report-to-module
binding"; JUnit 4 handling stays validated by gson (phase 1) instead.

**Criterion 2 (overall) closed on all four phases (2026-08-25):** phase 3's
parity, deferred in D-36 as "a Gradle equivalent needs its own design", turned
out to need no Gradle design at all - the standalone `sonar-scanner` CLI takes
sources, tests and the JaCoCo XML directly, which is exactly coverdict's own
input model (D-01/D-02), so `sonar-parity.ps1` gained a `-Scanner Cli` branch
instead (D-45). Result: exact match, 54.8 vs 54.8 (jacoco-line/line_coverage
also exact, 58.8 vs 58.8). All four phases now: gson 91.0, assertj 74.4,
junit-framework 54.8, dropwizard 81.1 - every one an exact match.

**Criterion-2 new-code parity is deferred, not open against M1** (2026-08-25,
user decision): it needs branch/PR analysis, which Community Edition does not
support at any configuration. It is not a coverdict defect and no amount of
work here closes it - it needs a Developer/Enterprise instance. Recorded as a
follow-up to run on such an instance when one is available; M1 does not wait
on it. The metric-id and numerator/denominator half of criterion 2 (which
applies to both overall and new-code output) has always held.

**M1 closing sweep done (2026-08-25).** Eight M0-specified behaviours had no
implementation; all are now real and documented (D-39 through D-45):
`--classpath`, `--config`, `customOracles`, `suppressions`, JUnit 4
`ExpectedException`, the `@Disabled`/`@Ignore` note, the generated
release artifacts with a working hard-rule-9 LGPL gate, and phase 3's sonar
parity. Three of them (`--config`, `ExpectedException`, the disabled note) had
no recorded deferral anywhere - they were silent gaps, not known debt.

**Regression check after the sweep:** all four corpus phases re-run through
the real CLI against their existing JaCoCo reports produce byte-identical
finding counts - gson 33, assertj 2148, junit-framework 51, dropwizard 0.
Nothing in the new configuration surfaces changes a default-configuration run,
which is the property that matters: every one of these features is opt-in.

M1 exit codes: `0` complete analysis regardless of findings; `1` reserved for
the M3 finding-based quality gate and never emitted by v0.1; `2` invalid
invocation/input; `3` incomplete or unverified-required evidence; `4` internal
failure.

## Testing infrastructure (2026-08-25)

`coverdict-playground` (private, separate repo, testing infra only - not a
public demo) is a small Java project with one deliberately constructed test
scenario per L0/L3 rule and a documented expected finding for each,
verified by hand with a real `analyze --base <ref> --mutation-report` run.
A checked-in copy of its source and JaCoCo report backs
`PlaygroundFunctionalTest` (`coverdict-cli`), which pins the six real L0
findings on every default `mvn verify` run. The L3 scenarios (mutation
evidence) need a real PIT subprocess and are not yet wired into an
automated test - next step, alongside `MutationRunnerIT`'s existing
`-Pmutation-it` profile. See that repo's README for the full scenario map
and the bug-repro workflow (shrink a real finding into a new scenario there).

## Later (sketches)

- **M2 — L2 feasibility and attribution spike.** Faz 0 (kill-switch) is
  **done**: D-47 confirms PIT's `CoverageExporterFactory` SPI is the L2
  engine, superseding D-13's sequential-JaCoCo-reset design; D-49/D-50 record
  the real `linecoverage.xml` schema and the `<clinit>` ambient-bucket
  mechanism. D-51 replaces the calling model itself: `EntryPoint.execute()`
  drives PIT programmatically with zero target-repo build-file changes,
  superseding Faz 0's throwaway-`pom.xml`-profile spike and answering
  Gradle repos too (no build-tool plugin needed at all). Evidence:
  `validation/runs/pit-spike/FINDINGS.md`.

  **Faz 2a-2e are done on assertj-core** (`org.assertj.core.api.*` scope,
  215 production + 164 test classes - not the full repo, a representative
  slice), replacing the original three-Jaccard-gate design (the shuffled-
  test-order gate turned out uncosable: `ReportOptions` exposes no seed/
  order control, and PIT owns test discovery order itself):
  - **2b scale:** coverage phase 20-29s for 17,039 class#method entries /
    39,923 line records - the real number replacing D-18's stale "25
    minutes" (whole-suite, wrong architecture) model.
  - **2c determinism:** two independent runs, byte-identical
    (mean Jaccard = 1.0 across all 39,923 records). Gate passed.
  - **2d cross-engine diagnostic** (not a gate - see FINDINGS.md §8 for why):
    compared against a full-suite JaCoCo report. 0.94% of directly
    comparable records disagreed, under the 5% escalation threshold, and
    the disagreement's shape is understood (PIT's `LineMapper` and JaCoCo's
    line table pick different "primary" lines for single-expression method
    bodies delegating straight into a lambda).
  - **2e ablation** (replaces the dropped shuffle gate - tests causation
    directly instead of self-consistency): 3/3 sampled multiplicity=1
    production-code lines genuinely lost coverage when their sole covering
    test was excluded and the suite re-run. Gate passed, 100%.

  **Also done: dropwizard (multi-module)**, `dropwizard-util` +
  `dropwizard-validation` merged into one `ReportOptions` (D-52) - PIT has
  no module concept, a multi-module target is just a classpath/source-dir
  union, confirmed by output containing classes from both modules and zero
  `pom.xml` changes in either. 2b (2-3s, 2241 records), 2d (0% cross-engine
  mismatch), and 2e (3/3 ablation) all passed cleanly. **2c (determinism)
  found real, bounded instability** (2240/2241, mean J=0.9998) traced to
  the corpus repo's own test code - `SelfValidatingValidatorTest` iterates
  JDK-reflection method lists with no ordering guarantee - not a coverdict
  or PIT defect, but a real class of risk for reflection-heavy test
  fixtures, worth a documented product limitation at M4.

  **junit-framework (Gradle) attempted; calling model confirmed, gates
  blocked on a real bytecode-version ceiling (D-53).** The `EntryPoint`
  calling model D-51 left unverified for Gradle now is: a `--init-script`
  (never touches `build.gradle.kts`) solved Isolated Projects and
  Configuration Cache obstacles specific to this repo, `git status` stayed
  empty. But `junit-vintage-engine`'s test/testFixtures source sets
  compile at Java 25 (no `--release` constraint, unlike its main source
  set's Java 7 target) and PIT 1.15.8's ASM cannot read that bytecode - the
  same ceiling Faz 0 hit in coverdict's own build, this time in code
  coverdict doesn't control. A recompile workaround (reading, not writing,
  the target repo) fixed one module's testFixtures before uncovering the
  same problem in a sibling module's - stopped rather than chasing a
  cascading fix.

  **M2's spike is complete on three corpus repos with an honest, mixed
  result:** clean pass (coverdict, assertj), pass with one root-caused
  bounded exception (dropwizard), and one real, documented ceiling
  (junit-framework) that ROADMAP's kill criterion already anticipates -
  "cut for that repo's shape." Not a mechanism failure: the calling model
  and all four gates hold everywhere PIT can read the bytecode. D-54:
  PIT 1.25.9 lifts the ASM ceiling but hits its own unresolved zero-block
  failure under JDK25 - staying on 1.15.8.

  **CLI evidence layer done (D-55):** `--per-test-report` calls
  `EntryPoint.execute()` (not the coverage-phase bypass D-47/D-51 implied -
  that hit an unreproduced minion crash) from a dedicated `PerTestDriver`
  subprocess `PerTestRunner` force-kills the moment `CoverdictLineExporter`
  writes its output file or a timeout elapses, whichever first - never
  waits for PIT's own mutation phase. Verified end-to-end against
  coverdict's own repo. Now optional enrichment for M4's `SUBSUMED_TEST`
  (D-61) rather than a required evidence layer - see M4 below.
- **M3 — First build integration + CI.** Ship Maven or Gradle first as decided
  from M0 dogfood, then the other only on demand. Add report provenance manifest,
  changed-findings baseline, quality-gate exit codes, and evaluate SARIF (O-02).
- **M4 — Mutation kill-set subsumption (`SUBSUMED_TEST`).** Redefined by
  D-61, superseding D-46's `COVERAGE_EQUIVALENT_CANDIDATE` three-stage
  equivalence gate (L2 overlap + L0 assertion match + L3 kill-set match, all
  required): that design fired almost exclusively on near-literal copy-paste
  tests SonarQube CPD already finds for free, and its L0 gate discarded the
  one finding shape coverdict's other evidence cannot get elsewhere -
  textually different tests proven behaviorally identical. `SUBSUMED_TEST`
  asks a directional question over L3 alone - does test A kill any mutant no
  other test also kills - computed as one kill-matrix intersection per test,
  no pairwise loop. `--mutation-report` (M5, done) is the only required
  evidence; L0/L2 become optional message enrichment, not gates. Gate on
  ≥ 90% precision on HIGH-confidence findings and a bounded firing rate (not
  a large fraction of a suite flagged at once - suite-reduction literature
  finds 40-70% "removable" routinely, which would itself be noise here).
  Depends on M5 exactly as originally scoped (kill-set data), no ordering
  inversion - M5 shipped before this was written.
- **M5 — Mutation integration.** Done: `--mutation-report`/`--mutation-
  classpath`/`--mutation-timeout`, `PSEUDO_TESTED_METHOD` (method-centric,
  D-56's gregor `RETURNS`+`VOID_METHOD_CALLS` approximation of Descartes'
  extreme mutation - covering tests are context, not proof that one test is
  worthless), diff scoping resolved by reusing L2's own mapping (O-05/D-12,
  closed by D-60 - no ArcMutate dependency needed). `setFullMutationMatrix
  (true)` carries the full kill-set M4's `SUBSUMED_TEST` needs. Not done:
  IDE surfaces (still just JSON). D-59's process-resource-growth question
  is now **closed by D-63**: `MutationDriver` was declaring every one of
  this repo's 716 test classes a covering-test candidate
  (`setTargetTests(Glob.toGlobPredicates(List.of("*")))`), which under
  `setFullMutationMatrix(true)` forced a full-suite coverage re-gather once
  per target method being probed - traced live via a verbose PIT capture
  (the same 19-test class's coverage regathered six times in one second)
  and fixed by scoping `targetTests` to the target class's own package.
  `MutationRunnerIT` went from reliably hitting its 90s budget every run to
  completing in ~1.1s, twice, back to back. `--mutation-report`'s 5-minute
  default timeout stays as a sensible ceiling, no longer a blind safety
  margin around an unknown risk.

  **L2/L3 subprocess observability done (D-64, 2026-08-27):** the WTA
  dogfood (see the classpath-UX backlog item below) hit `service` budget
  exhaustion, a `grpc` minion crash, and empty `app`/`data` per-test
  evidence, and could root-cause none of them - the subprocesses' output
  was either discarded (`PerTestRunner`) or cut to a 20-line tail
  (`MutationRunner`), and nothing reported progress during a run that can
  take tens of minutes. Both runners now merge and drain their child's
  streams, report live progress to the CLI's own stderr (a target-class
  count before starting, a heartbeat while running, done/failed at the
  end), and `--diagnostics-dir` tees the whole (optionally verbose)
  subprocess log to a file per module. Three matching silent returns
  closed under hard rule 3a: `MUTATION_NO_CHANGED_TARGETS` /
  `PER_TEST_NO_CHANGED_TARGETS` when a module has nothing mapped to mutate
  (WTA's first run silently hit this on every module), and
  `MUTATION_EMPTY_EVIDENCE` / `PER_TEST_EMPTY_EVIDENCE` when the engine
  runs but resolves zero records. Verified against a real PIT subprocess
  (`PlaygroundMutationIT`), not stubbed. Exit-code semantics were
  deliberately left unchanged - see the WTA dogfood note below for what
  is still open.
- **WTA dogfood, next round pending:** D-64 is diagnostics only, not a fix.
  Still unexplained: why `service` (219 mapped files, root-commit-as-diff)
  never finished a 1800s mutation budget - is the counter stuck at 0
  (never reached the mutation phase) or merely slow; why `grpc`'s coverage
  minion died with `UNKNOWN_ERROR` (bytecode/JDK mismatch was ruled out by
  the user directly - `javap` showed major version 65 against a running
  JDK 21.0.2); and why `app`/`data` per-test evidence resolved to `entries:
  []` despite the same target classes producing real mutants under
  `--mutation-report` (proves the classes are covered - the gap is in L2's
  own coverage-to-line resolution, not WTA's tests). Re-run with the D-64
  jar and `--diagnostics-dir` before designing any of these three fixes.
- **Backlog:** standalone HTML · AI-assistant skill (agent reads verdict JSON,
  writes tests for gaps it names, reruns, interprets the result through
  coverdict again) · VS Code extension (inline per-line coverage gutter
  annotations, toggleable) · IntelliJ plugin (same gutter/panel concept as the
  VS Code extension) · one-click "send this verdict to the AI assistant"
  action from either IDE extension, aimed at popular in-IDE AI tools
  (Copilot, Cursor, Windsurf, ...) so they can read and act on findings
  without the user copy-pasting JSON · installable CLI distribution (so
  people can actually get and run coverdict, not just build it from source)
  · separate **public** playground repos (distinct from the private
  `coverdict-playground` testing fixture) seeded with realistic test-quality
  issues, used for outreach - opening coverdict-found issues against them -
  once the tool itself is public · second build integration if not justified
  in M3 · non-Java languages · Truth-specific `NULL_CHECK_ONLY`/`TAUTOLOGICAL_ORACLE`
  weak-oracle patterns (D-31 added Truth as a recognized oracle for
  `NO_RECOGNIZED_ORACLE`/`CATCH_ORACLE_WITHOUT_FAIL` only; the other two
  rules still only recognize JUnit/AssertJ shapes) &#x2713; ~~extend
  `OracleRecognizer`'s helper traversal to same-compilation-unit public
  static helpers~~ done, D-33 &#x2713; ~~investigate JavaParser symbol-solver
  resolution of overloaded calls whose argument is a lambda~~ done, D-33 -
  turned out not to be about lambdas at all (an unresolvable parameter type
  on the callee's own declaration), fixed with a same-file name-match
  fallback · SoftAssertions instance-receiver recognition (`softly.
  assertThat(x)`/`softly.then(x)`) - D-34 found ~5% of assertj's
  `NO_RECOGNIZED_ORACLE` findings are this shape; `isChainAnchor` is built
  on static (declaringType, methodName) pairs, so recognizing an instance
  receiver is a structural `OracleRecognizer` change, not an allowlist edit
  · `WithAssertions` interface delegation (a test class implementing that
  interface gets `assertThat(...)` resolved to its own default methods,
  declaring type `org.assertj.core.api.WithAssertions`, not `Assertions` -
  D-34's assertj precision review, low volume but real) ·
  `InstanceOfAssertFactory` chains (a terminal assertion called directly on
  a factory-returned assert object, e.g. `TEMPORAL.createAssert(actual).
  isCloseTo(...)`, never touching `Assertions`/`BDDAssertions` at all - a
  structurally different gap from a missing allowlist entry, would need
  recognizing terminal calls on AssertJ's own `AbstractAssert`-typed
  values) · chain-terminal oracle recognition for a library that owns only
  the terminal link of a chain rooted at the test's own code (D-36, JUnit
  Platform Testkit's `Events`/`EventStatistics`; a same-session fix attempt
  was reverted - real corpus code has no classpath and no import naming the
  terminal's type, so this is blocked on real `--classpath` support, not an
  allowlist edit) · `sameFileNameMatch`'s same-file-overload ambiguity
  bailout should consider call-site arity before giving up (D-36; today it
  bails on any name collision even when only one candidate has the right
  parameter count for that specific call) · D-61's described `SUBSUMED_TEST`
  L2 message enrichment (dominator's assertions noted as textually identical
  vs. structurally different when `--per-test-report` is present) is not
  implemented - `SubsumedTestRule` never reads `perTest` evidence today;
  confirmed by a real coverdict-playground run where enabling
  `--per-test-report` added an (empty, for that scenario) `perTest` block to
  the JSON but left the `SUBSUMED_TEST` finding's message byte-identical.
  · L2/L3 classpath UX gap (found in WTA dogfood, 2026-08-26): `--per-test-
  classpath`/`--mutation-classpath` require the user to hand-run a separate
  `mvn dependency:build-classpath` recipe outside coverdict and get every
  detail right (`-pl` changes Maven's cwd, `-Dmdep.outputFile` must be
  relative, module output dirs must be prepended) - one wrong flag produces
  a classpath list with zero code paths, `MUTATION_CLASSPATH_MISSING`/
  `PER_TEST_CLASSPATH_MISSING` fires, and the module's evidence is silently
  empty while the run still reports `complete`/exit 0 (compounds the still-
  open "empty evidence looks like success" gap above). coverdict never
  shells out to a build tool today (hard rule "verdict layer, never own
  engines" - see AGENTS.md); whether that boundary should flex for exactly
  this one Maven-only convenience path, or whether the fix is instead a
  loud preflight check (did the classpath file actually resolve to code
  paths / were tests actually compiled recently) before the PIT subprocess
  even starts, is undecided - needs its own design pass, not a silent
  default.
  Each remaining item gets its own design pass at its milestone, not now.

## Kill and pivot criteria

- If a M1 rule misses 90% HIGH precision after two documented calibration
  rounds, remove or downgrade that rule; aggregate precision cannot hide it.
- If complete source/report mapping cannot be guaranteed, v0.1 ships no coverage
  success verdict until a build integration supplies trustworthy provenance.
- If any of M2 Faz 2's four gates (2c determinism, mean J = 1.0 same-order
  repeat - a shortfall is only acceptable when root-caused to the target
  repo's own code rather than the L2 mechanism, as dropwizard's 0.9998
  was, D-52; 2e ablation, 100% of sampled multiplicity=1 claims verified
  causally; 2d cross-engine diagnostic, disagreement understood and under
  5%; 2a calling model, zero target-repo build-file changes) fails
  unexplained on a dogfood repository, L2 is cut for that repo's shape; if
  it fails on all of them, L2 is cut and the product remains L0+L1(+L3).
  Passed on coverdict and assertj; passed with one root-caused, bounded
  exception on dropwizard; cut for junit-framework's shape specifically
  (D-53 - PIT's ASM cannot read that repo's test-source bytecode), which
  is this criterion resolving as designed, not an open question. M2's
  spike is done: three repos, three different outcomes, all understood.
- If dogfood users do not repeat the workflow or findings are predominantly
  ignored/waived, stop integration work and revisit the product wedge.
- If `sonar-compatible` parity fails against the pinned internal setup, remove
  that mode name rather than publishing an approximate compatibility claim.
- If two dogfood repos' manually-verified `PSEUDO_TESTED_METHOD` precision
  falls under 90%, downgrade the rule to `INFO` or cut it - gregor's
  `RETURNS`+`VOID_METHOD_CALLS` approximation of Descartes (D-56) is an
  unvalidated substitution until measured against real findings, not just
  against the mechanism working. If D-59's process-resource-growth question
  is not root-caused before a second dogfood repo is attempted, `--mutation-
  report` stays off by default and undocumented as a recommended flag,
  regardless of finding precision.
