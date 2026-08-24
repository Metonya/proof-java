# dropwizard (dropwizard-util + dropwizard-validation) - precision summary (M1c criterion 4)

Repo: `dropwizard/dropwizard` @ `87940b9728fa6cce6598a0433da97ace373ee828`
(`release/4.0.x`), modules `dropwizard-util` + `dropwizard-validation` bound
together in one coverdict invocation (D-37 multi-module binding). Run:
`verdict-no-vcs.json`. Labeler: claude-sonnet-5 (2026-08-24).

## Result

**0 findings, across every rule and every confidence tier**, over 105 real
`@Test` methods (`verdict-no-vcs.json`: `findings: []`, `analysis.status:
complete`, `incompleteReasons: []`). `labels.csv` is header-only - there is
no population to sample.

This is reported as a genuine result, not a silent gap (hard rule 3a): it
is corroborated, not just asserted.

- The oracle scan definitely ran: `coverage.overall` reports real,
  non-trivial numbers (jacoco-line 84.8%, 419/494; sonar-compatible 81.1%,
  548/676) computed from the same JaCoCo XML the scan also walks for
  test-method source, and `analysis.status` is `complete`.
- Both modules' test suites are almost entirely `assertThat(...)` (assertj,
  1083 occurrences across the 105 `@Test` methods - roughly 10 assertions
  per test) plus 2 Mockito `verify(...)` calls - both already fully covered
  by `OracleAllowlist`'s prefix-based `isChainAnchor`/`isUnconditionalOracle`
  (D-34). No Truth, no Hamcrest, no custom in-house assertion helpers.
- `grep` confirms zero `@Disabled`/`@Ignore` methods in either module -
  nothing to trip the undocumented disabled-test-note gap
  (docs/rules/README.md promises a "disabled test" note that isn't
  implemented; not exercised here either way).
- No `try`/`catch`-without-`fail()` shape found by inspection -
  `CATCH_ORACLE_WITHOUT_FAIL`'s 0-count is consistent with the source, not
  unexplained.

## What this phase does and does not establish

**Establishes for real, for the first time (D-37):** `ModuleBinder` binding
two real modules with two distinct roots and two distinct JaCoCo reports in
one invocation, and coverdict's `overall` metrics correctly merging across
them (previously only unit-tested via a degenerate same-root case, see
`ModuleBinderTest`). `CHANGED_JAVA_OUTSIDE_MODULES` also fired correctly and
as expected on `--base <pin>~50`: a real changed file in the (undeclared)
`dropwizard-hibernate` module was reported incomplete rather than silently
dropped.

**Does not establish:** JUnit 4 handling, or any new oracle-recognition
edge case - this pinned commit has no JUnit 4 at all (see run-log and the
manifest correction below), and the two chosen modules' assertion style
happens to hit no known allowlist gap. Criterion 4's HIGH-precision bar is
met trivially (n=0, vacuously), the same shape as junit-framework's n=1
HIGH tier - not a strong precision signal on its own, but not a hidden one
either.

## Manifest correction (not a phase-4 finding to fix, a documentation fix)

`docs/M0-VALIDATION-MANIFEST.md`'s phase table lists dropwizard's "Forces"
as "multi-module Maven, **mixed JUnit 4+5**, report-to-module binding".
Verified false at this pin: `git grep` for `import org.junit.Test;`,
`@RunWith`, `import org.junit.Rule;`, and `import static org.junit.Assert`
across the entire checkout returns zero matches; every module's pom
explicitly `<exclusion>`s `junit:junit`. `release/4.0.x` is fully migrated
to JUnit 5 (321 `@org.junit.jupiter.api.Test` methods repo-wide). This is
likely stale from an earlier pin predating the 4.0 migration. See D-38 and
the corrected manifest row.
