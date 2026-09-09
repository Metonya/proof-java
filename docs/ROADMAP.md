# Roadmap

Only the current milestone is specified in detail. Later ones are sketches,
replanned at each gate. The phase-by-phase build log for everything already
shipped lives in the git history, not here.

## Evidence levels

Used throughout the project, and the vocabulary the CLI, the schema and the
report all share:

- **L0 — static oracle evidence.** Conservative source analysis. Makes no
  runtime claim about what a test executed.
- **L1 — aggregate coverage.** JaCoCo XML plus a git diff. No per-test linkage.
- **L2 — per-test execution fingerprints.** Opt-in, for redundancy candidates,
  subject to the attribution limits in D-18.
- **L3 — mutation evidence.** Method-centric PIT findings. A surviving mutant
  does not by itself prove that an individual test verifies nothing.

## Where the project is

**M0 — product and contract readiness: complete (2026-08-23).** The persona,
input model, validation manifest, security policy and release contract are
checked in as `docs/PERSONA.md`, `docs/INPUT-MODEL.md`, `docs/VALIDATION.md`,
`docs/SECURITY-POLICY.md` and `docs/RELEASE-CONTRACT.md`. Production analyzer
work was gated on this closing (D-14).

**M1 — trustworthy CLI wedge (current, v0.1).** Shipped: evidence-safe diff
coverage (L1), the conservative static oracle critic (L0, six rules in
`docs/rules/`), `doctor`, the verdict JSON contract and its schema, the HTML
report, and the agent skill in `skills/proof-java/`. L2 and L3 are implemented
and opt-in.

**M2 — L2/L3 feasibility: spike done, three repos, three outcomes.** L2 works
on this repo and on assertj; it works on dropwizard with one root-caused,
bounded exception (D-52); it is cut for junit-framework's shape specifically,
because PIT's ASM cannot read that repo's test-source bytecode (D-53). That is
the kill criterion resolving as designed, not an open question.

## Known limitations worth stating plainly

- **`newCode` is inert when an agent only writes tests.** In a run where the
  only changed file is a test file, `coverage.newCode` is `0/0` in all three
  modes, because test files are correctly classified as excluded (D-27). The
  entire L1 half of the tool has nothing to say in the "write tests for existing
  code" branch of `docs/PERSONA.md`. Anyone building a CI gate on `newCode`
  needs to know that branch exists.
- **L2/L3 need a JDK the embedded PIT can read class files for.** Above that
  ceiling the analysis reports incomplete rather than collecting; see
  `PitJdkSupport` and the support table in the README.
- **Convergence can be reported more strongly than it holds.** A
  `PSEUDO_TESTED_METHOD` finding disappears when a mutant becomes `TIMED_OUT`
  (a non-final status makes the rule skip the method), which is not the same as
  the mutant being killed.

## Later (sketches)

- **M3 — a finding-based quality gate.** Exit code `1` is reserved for it and
  deliberately unused in v0.1: a gate that fails a build needs precision
  numbers per rule and per confidence tier first.
- **Non-Java engines.** The verdict JSON is deliberately language-neutral
  (`proof.config.json`, `proof-verdict.json`), so a sibling engine can share the
  schema. Nothing is designed for it beyond keeping those names free of Java.
- **IDE surface.** A separate repository consumes the same JSON through
  `render-html` rather than re-analyzing; `--file-coverage` exists for its
  gutter annotations.

## Kill and pivot criteria

These are the conditions under which a piece of this tool gets cut rather than
defended:

- If an M1 rule misses 90% HIGH precision after two documented calibration
  rounds, remove or downgrade that rule. Aggregate precision may not hide it.
- If complete source/report mapping cannot be guaranteed, v0.1 ships no coverage
  success verdict until a build integration supplies trustworthy provenance.
- If L2's determinism, ablation or cross-engine gates fail unexplained on a
  dogfood repository, L2 is cut for that repo's shape; if they fail on all of
  them, L2 is cut and the product remains L0+L1(+L3).
- If dogfood users do not repeat the workflow, or findings are predominantly
  ignored or waived, stop integration work and revisit the product wedge.
- If `sonar-compatible` parity fails against the pinned setup, remove that mode
  name rather than publishing an approximate compatibility claim.
- If two dogfood repos' manually-verified `PSEUDO_TESTED_METHOD` precision falls
  under 90%, downgrade the rule to `INFO` or cut it. gregor's
  `RETURNS`+`VOID_METHOD_CALLS` approximation of Descartes (D-56) is an
  unvalidated substitution until measured against real findings, not merely
  against the mechanism working.

- `render-html` on a sibling engine's verdict prints `proof-java <version>` in
  its footer; `VerdictDocument` carries no tool name (D-99).
