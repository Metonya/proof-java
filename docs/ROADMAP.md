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
  issues, ~95% real line coverage. Separate from the `sonar-compatible`
  parity ground truth mentioned in the
  kill criteria below, which targets the M1c validation corpus, not our code.

### M1b — Conservative static oracle critic

- Use JavaParser plus Symbol Solver with explicit source roots, language level,
  and classpath. Parser success alone is not semantic resolution.
- Implement `NO_RECOGNIZED_ORACLE`, `TAUTOLOGICAL_ORACLE`,
  `CATCH_ORACLE_WITHOUT_FAIL`, and `NULL_CHECK_ONLY` to their checked-in rule
  specifications. `NULL_CHECK_ONLY` is advisory; none suggests deletion.
- Recognize the explicitly supported JUnit/assertion/mock APIs and configured
  custom oracle providers. Unresolved helpers or types are MEDIUM/INCONCLUSIVE,
  never HIGH absence evidence.
- Support rule/path suppression in the configuration. Baselines and
  changed-findings-only gating must exist before CI gating, not necessarily v0.1.

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
  non-Java languages. Each gets its own design pass at its milestone, not now.

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
