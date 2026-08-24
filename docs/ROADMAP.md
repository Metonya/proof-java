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
   **Done** (2026-08-23): `LICENSE` + `docs/RELEASE-CONTRACT.md`.

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

- Custom oracle providers (`customOracles` configuration) and `--classpath`
  are still open - the allowlist and resolution layers are already shaped to
  add them without a rework.
- Rule/path suppression, baselines, and changed-findings-only CI gating are
  still open (M3 pre-CI work, as originally scoped).

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

Phases 2-4 and criterion-2 new-code parity (pending a Developer Edition
SonarQube instance, or acceptance that Community Edition caps this) remain
open. Phase 2's own criterion-4 precision labeling and criterion-2/6 runs
are separate next steps, not done in this pass.

M1 exit codes: `0` complete analysis regardless of findings; `1` reserved for
the M3 finding-based quality gate and never emitted by v0.1; `2` invalid
invocation/input; `3` incomplete or unverified-required evidence; `4` internal
failure.

## Later (sketches)

- **M2 — L2 feasibility and attribution spike.** Before investing in broad
  integration, test D-13/D-18 on real repositories: constructors and field
  initializers, lifecycle methods, parameterized/dynamic invocations, retries,
  async work, static state, child JVMs, and multi-module forks. Use JaCoCo probe
  vectors as observed execution fingerprints; do not claim behavioral identity.
- **M3 — First build integration + CI.** Ship Maven or Gradle first as decided
  from M0 dogfood, then the other only on demand. Add report provenance manifest,
  changed-findings baseline, quality-gate exit codes, and evaluate SARIF (O-02).
- **M4 — L2 redundancy productization.** Only after the M2 spike and at least
  90% precision on HIGH findings. Use `COVERAGE_EQUIVALENT_CANDIDATE` language;
  no deletion suggestion follows from coverage identity alone.
- **M5 — Mutation integration.** Start method-centric with
  `PSEUDO_TESTED_METHOD`; covering tests are context, not proof that one test is
  worthless. Resolve diff scoping first (O-05/D-12). IDE surfaces render JSON.
- **Backlog:** standalone HTML · AI-assistant skill (agent reads verdict JSON,
  writes tests for gaps it names, reruns, interprets the result through
  coverdict again) · VS Code extension (inline per-line coverage gutter
  annotations, toggleable) · second build integration if not justified in M3 ·
  non-Java languages · Truth-specific `NULL_CHECK_ONLY`/`TAUTOLOGICAL_ORACLE`
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
  values). Each remaining item gets its own design pass at its milestone,
  not now.

## Kill and pivot criteria

- If a M1 rule misses 90% HIGH precision after two documented calibration
  rounds, remove or downgrade that rule; aggregate precision cannot hide it.
- If complete source/report mapping cannot be guaranteed, v0.1 ships no coverage
  success verdict until a build integration supplies trustworthy provenance.
- If M2 cannot produce stable, contamination-detectable fingerprints on the
  dogfood repositories, L2 is cut and the product remains L0+L1(+L3).
- If dogfood users do not repeat the workflow or findings are predominantly
  ignored/waived, stop integration work and revisit the product wedge.
- If `sonar-compatible` parity fails against the pinned internal setup, remove
  that mode name rather than publishing an approximate compatibility claim.
