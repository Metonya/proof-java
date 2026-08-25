# Validation manifest (M0 deliverable 5)

Everything needed to reproduce a coverdict validation run. Any number
published from these repositories must cite this manifest and the commit of
the coverdict version that produced it.

## Validation corpus (M1c criterion 5)

Distinct from the dogfood proxies in `M0-PERSONA.md`: dogfood observes the
workflow, this corpus proves correctness at pinned commits. Each repo is
pinned by SHA; a re-pin is a manifest edit, never a silent bump.

| Phase | Repo | Pinned commit | Forces |
|---|---|---|---|
| 1 canary | `google/gson` | `dae37cf0fe12235b76fb09f01118a0a8c8823f42` | small, fast smoke run; JUnit 4 suite |
| 2 fluent | `assertj/assertj` | `4c5ab4862668e769d0e72492f400bd919469455d` | fluent/custom assertion DSL recognition |
| 3 constructs | `junit-team/junit-framework` | `9cd9a3cfb6cd98aec355bd49fc8d801058762441` | Gradle build; dynamic-test and nested source constructs |
| 4 multi-module | `dropwizard/dropwizard` (`release/4.0.x`) | `87940b9728fa6cce6598a0433da97ace373ee828` | multi-module Maven, report-to-module binding |

Phase 3 is a Gradle-built repository. Per D-23 this is not "Gradle support":
the CLI consumes source roots and JaCoCo XML explicitly (D-01/D-02), so the
host build tool is irrelevant to it.

## Toolchain (pinned)

- **JDK for building coverdict and the corpus:** Temurin 17.0.17+10.
  `<release>17</release>`; source encoding UTF-8 declared explicitly (the
  benchmark machine's platform encoding is Cp1254 — never rely on the
  default).
- **Maven:** 3.9.16 · **Git:** 2.46.0 · **JaCoCo:** 0.8.x, exact version
  recorded per run (report format is the contract, not the tool version).

## Commands

Per corpus repo, from its checkout at the pinned commit:

```bash
git checkout <pinned-sha>
mvn -q -B verify            # Maven repos; phase 3 uses ./gradlew build
# JaCoCo XML lands at target/site/jacoco/jacoco.xml (per module)
```

Phase 3's `./gradlew build` in practice (D-36): `run-corpus-phase.ps1
-BuildTool Gradle -GradleModule <name>` runs
`gradlew.bat --no-daemon :<module>:test :<module>:jacocoTestReport`, with an
`--init-script` (never a checkout edit) turning on JaCoCo's XML report
(off by default in Gradle's own plugin). JaCoCo XML lands at
`<module>/build/reports/jacoco/test/jacocoTestReport.xml`.

Then, from the coverdict checkout:

```bash
java -jar coverdict.jar analyze --repo <corpus-repo> \
  --base <pinned-sha>~50 --report <jacoco-xml> --out verdict.json
```

The exact per-repo invocation (module ids, report paths, exclusions) is
recorded in the run log next to its results, since it differs per repo.

## Corpus phase harness (M1c-2)

**Prerequisite before running `sonar-parity.ps1` on any phase:** confirm
`C:\Users\Mert\.wslconfig` has a `[wsl2] memory=` cap set (currently 8GB) -
D-35 records a real incident where scanning assertj-core (~4600 test
files) without this cap froze the whole machine and required a hard
restart. junit-framework and dropwizard are both likely larger than
assertj; never skip this check for them.

`validation/scripts/` holds three reusable, repo-agnostic scripts that run
each corpus phase's commands above without new code per phase (D-30):

- `run-corpus-phase.ps1` — clone/checkout the pinned commit, bind JaCoCo via
  CLI goals (no permanent pom edit) or, for a Gradle repo (`-BuildTool
  Gradle`, D-36), via a temp `--init-script` (never a checkout edit), run
  coverdict in `--base <pin>~50` and `--no-vcs`.
- `sonar-parity.ps1` — overall-scope `sonar-compatible` vs SonarQube UI
  comparison. Three modes: a single Maven module (run from inside the
  module's own directory, not via reactor `-pl`), a Maven reactor subset
  (`-pl a,b -am`, D-37), and `-Scanner Cli`, which drives the standalone
  `sonar-scanner` and therefore needs no build tool at all - that is how the
  Gradle phase is measured (D-45, superseding D-36's deferral). New-code
  parity still needs a non-Community-Edition instance and is deferred as a
  follow-up, not an open M1 item.
- `benchmark-phase.ps1` — cold + 5-warm wall time and peak working-set
  memory around a coverdict invocation.
- `scaffold_labels.py` — seeded 100-sample (per rule) CSV scaffold from a
  verdict JSON's `findings[]`, per the labeling protocol below.

Each phase's real output is archived under `validation/runs/<phase-name>/`
(run-log.md, verdict JSONs, benchmark.json, sonar-parity.md, labels.csv) —
real measurements, not byte-deterministic goldens, so not pinned in
`validation/SHA256SUMS`. See `validation/runs/gson/` for the phase 1
canary.

## Fixture and schema integrity

`validation/SHA256SUMS` pins every rule fixture, JaCoCo XML fixture, unified
diff fixture, and schema/golden file (`fixtures/**/*.java`,
`fixtures/**/*.xml`, `fixtures/**/*.diff`, `schema/**/*.json`).
Verify before any labeling run:

```bash
sha256sum -c validation/SHA256SUMS
```

Fixtures and schema files are stored LF-normalized via `.gitattributes`, so
these hashes hold identically on Windows, macOS, and Linux (D-22).

`validation/SHA256SUMS` also pins `fixtures/verdicts/*.json` (M1c-1, D-29):
real tool OUTPUT, not hand-authored, byte-compared against a fresh CLI run
over a synthetic in-repo fixture repo by `VerdictGoldenTest`. Regenerate
after a deliberate, reviewed output-format change:

```bash
mvn -q -pl coverdict-cli -am test -Dtest=VerdictGoldenTest -Dcoverdict.regenerateGoldens=true
```

Then hand-edit each regenerated file to replace its literal `tool.version`
value with the placeholder `${tool.version}` before committing (the test
substitutes it back in at comparison time), and regenerate
`validation/SHA256SUMS`.

## Labeling protocol (M1c criterion 4)

- Scope: per repo **and** per rule. Review all findings up to 100; above
  that, a seeded deterministic sample of 100 (seed recorded with the run).
- Each reviewed finding gets a label — `true-positive`, `false-positive`, or
  `unclear` — plus a one-line reason, checked into the run's results file.
  `unclear` counts against precision; it is never silently dropped
  (hard rule 3a).
- Precision is reported per rule **and per confidence tier**; an aggregate
  number may never hide a failing tier. HIGH precision must be ≥ 90%.
- Labels are written before seeing whether the tier passes, and the labeler
  is recorded.

## Benchmark machine and measurement

- **Machine:** Windows 11 Pro 26200, x86-64. Exact CPU/RAM recorded in the
  first run log (M1c criterion 6 requires hardware with the numbers).
- **Analyzer-only** median and p95 wall time plus peak memory. Test count is
  never the performance proxy — coverdict does not run tests. Recorded
  alongside: XML size, Java LOC, module count, JDK, exact command.
- **Cold:** fresh JVM, OS file cache dropped, first run discarded is *not*
  allowed — the first run *is* the cold number.
- **Warm:** 5 consecutive runs in the same conditions; report median and p95.

## Reproducibility spot-check

Performed 2026-08-23 on this machine before the manifest was accepted:
`sha256sum -c validation/SHA256SUMS` → all files OK, and
`python schema/validate-goldens.py` → all goldens pass. Corpus build
commands are checked when M1c runs start; they are not asserted here.
