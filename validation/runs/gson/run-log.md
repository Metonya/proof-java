# gson — M1c-2 phase 1 canary run log

Repo: `https://github.com/google/gson.git` @ `dae37cf0fe12235b76fb09f01118a0a8c8823f42`
(pinned in `docs/M0-VALIDATION-MANIFEST.md`; confirmed reachable and still at
`main`'s tip on 2026-08-24).
Clone: `C:\Users\Mert\Desktop\coverdict-corpus\gson` (sibling of the coverdict
repo, never committed to it).
Module analyzed: `gson` (core, JUnit4/Truth-heavy, ~123 test files) — the
manifest's "small, fast smoke run" scope. `extras`, `metrics`, `proto`,
`test-jpms`, `test-graal-native-image`, `test-shrinker` are declared as
separate gson Maven modules but intentionally out of this canary's scope;
their changed files inside the `~50` diff window surface as
`CHANGED_JAVA_OUTSIDE_MODULES` in `verdict-base.json` (expected, not a bug —
see "Known incompletes" below).

Toolchain: JDK 17.0.17 Temurin, Maven 3.9.16, git 2.46.0.windows.1.
coverdict.jar: `coverdict-cli/target/coverdict.jar`, built fresh from this
session's HEAD before running.

## Build: binding JaCoCo without touching gson's pom permanently

```
mvn -q -B -pl gson -am -Dtest='!EnumWithObfuscatedTest,!OSGiManifestIT' -DfailIfNoTests=false ^
    clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report
```

0.8.13 matches coverdict's own pinned `jacoco.plugin.version` (`pom.xml:60`).
Two real obstacles hit and resolved, both specific to gson's own build, not
to coverdict:

1. **`gson/pom.xml`'s surefire config hardcodes `<argLine>--illegal-access=
   deny</argLine>`** (a literal string, not a `${...}` expression), which
   silently discards JaCoCo's injected javaagent `argLine` property — no
   `jacoco.exec` was ever produced. Fixed with a **local, throwaway** one-line
   patch (`--illegal-access=deny ${argLine}`) to the cloned pom, never
   committed anywhere and irrelevant to the coverdict repo.
2. With the agent now attached, the full surefire classpath scan crashed
   (`NoClassDefFoundError: record`) on `EnumWithObfuscatedTest`'s ProGuard-
   obfuscated companion class (`gson/pom.xml` obfuscates
   `EnumWithObfuscatedTest$Gender.class` via `proguard-maven-plugin` bound to
   `process-test-classes`, then copies it back into `target/test-classes`
   under its original name) — the obfuscated bytecode does not survive
   JaCoCo's on-the-fly instrumentation/reflection scan. Excluded via
   `-Dtest='!EnumWithObfuscatedTest'`. A second class, `OSGiManifestIT`
   (Failsafe-style, `*IT` suffix but picked up by surefire's scan here too),
   fails for an unrelated, self-documented reason: it asserts it is being
   run against the packaged jar's `META-INF/MANIFEST.MF`, and explicitly
   tells you in its own failure message to run via `mvn clean verify`, not
   a bare `test` phase. Excluded too. Both exclusions are pre-existing test
   design assumptions in gson itself, not something JaCoCo or coverdict
   caused, and only 2 of gson's ~123 test files are affected.

Result: 4621 of 4625 tests run (4 `OSGiManifestIT` cases + `Enum
WithObfuscatedTest`'s tests excluded), all passing.
`gson/target/site/jacoco/jacoco.xml` produced, real coverage data.

## coverdict runs

```
java -jar coverdict.jar analyze --repo . \
  --base dae37cf0fe12235b76fb09f01118a0a8c8823f42~50 \
  --module gson=gson --source-roots gson=gson/src/main/java \
  --test-roots gson=gson/src/test/java --report gson=gson/target/site/jacoco/jacoco.xml \
  --language-level 11 --out verdict-base.json
```
`--language-level 11` matches gson's own `maven.compiler.testRelease` (its
main code targets Java 8; its own pom comment explains the 11 test bytecode
level exists so JDK-version-conditional test code can be written).
Exit 3 (incomplete) — see "Known incompletes" below; overall coverage is
still present in the same document (D-26 semantics, confirmed working on a
real external repo).

```
java -jar coverdict.jar analyze --repo . --no-vcs \
  (same --module/--source-roots/--test-roots/--report/--language-level) \
  --out verdict-no-vcs.json
```
Used for the criterion-2 overall-scope Sonar parity comparison (no diff mode
needed for an overall-only check).

## Results

- **Overall coverage:** `jacoco-line` 92.4% (5094/5515), `strict-line` 91.3%,
  `sonar-compatible` 91.0% (7522/8267).
- **New code (`~50`-commit window):** `jacoco-line` 97.4% (147/151),
  `sonar-compatible` 96.9%. 83 changed files: 34 mapped, 33 excluded (test
  files, per D-27), 4 non-executable, 12 unknown.
- **Findings:** 1090 total — 1088 `NO_RECOGNIZED_ORACLE`, 2
  `CATCH_ORACLE_WITHOUT_FAIL`. See `precision-summary.md` and `labels.csv`
  for the calibration-round-1 analysis: **root cause is Google Truth not
  being in the D-24 recognized-oracle allowlist**, confirmed on a 100-item
  seeded sample (seed 42). HIGH-tier precision on this sample: 4.2%
  (4/96) — well under the 90% bar, documented as calibration round 1 of 2
  per the kill criteria, with a concrete, scoped fix identified (add Truth
  to the allowlist) rather than evidence the rule itself is unsound.

## Known incompletes (expected, not defects)

- `CHANGED_JAVA_OUTSIDE_MODULES` (12 files): real changes in the `~50`
  window touch `extras/`, `metrics/`, `proto/`, `test-shrinker/` — gson
  Maven modules intentionally not declared for this canary's `--module`
  scope. Exercises the exact fail-closed behavior hard rule 3a requires:
  the tool refuses to silently drop these from the picture rather than
  guessing they don't matter.
- `UNPARSEABLE_TEST_SOURCE` (1 file): `Java17RecordTest.java` requires a
  higher language level than 11 (records). Skipped, run continues (hard
  rule 2a) — and independently cross-confirmed: SonarQube's own scanner
  (run separately for the parity check below) hit and reported the exact
  same file with the exact same root cause ("Unable to parse ... Parse
  error at line 90").

## Sonar parity (criterion 2, overall scope only)

Local SonarQube instance confirmed **Community Edition** (`api/server/
version` 26.3.0.120487; branch/PR analysis unsupported on this edition,
so **new-code** `sonar-compatible` parity is not measurable here and stays
open — documented limit, not a silent pass). Overall-scope result via
`validation/scripts/sonar-parity.ps1`:

| Metric | coverdict | SonarQube |
|---|---|---|
| sonar-compatible / coverage | 91.0 | 91.0 |
| jacoco-line / line_coverage (bonus cross-check) | 92.4 | 92.4 |

Delta 0.0, well within ±0.1. **PASS.** See `sonar-parity.md`.

## Benchmark (criterion 6)

Via `validation/scripts/benchmark-phase.ps1` (`--no-vcs` invocation, cold +
5 warm): cold 182.5 ms, warm median 182.5 ms, warm p95 184.5 ms, peak
working set 57.1 MB. Machine: Windows 11 Pro 26200, x86-64; JDK 17.0.17
Temurin. See `benchmark.json`. XML size / Java LOC / module count: 5515
executable lines, 123 test files, 1 module (this phase's scope).

## Precision labeling (criterion 4)

`validation/scripts/scaffold_labels.py` sampled 100 `NO_RECOGNIZED_ORACLE`
(seed 42) + the full 2 `CATCH_ORACLE_WITHOUT_FAIL` population into
`labels.csv`. Each row's real test source was read and labeled by the
agent (`classify_findings.py` + `fill_gson_labels.py` in this directory
document the exact derivation); user spot-check is a follow-up, not part
of this session. Full analysis in `precision-summary.md`.
