# L0 rule specifications — shared contract (M0 deliverable 4)

Applies to all four v0.1 L0 rules: `NO_RECOGNIZED_ORACLE`,
`TAUTOLOGICAL_ORACLE`, `CATCH_ORACLE_WITHOUT_FAIL`, `NULL_CHECK_ONLY`.
Detection uses JavaParser + Symbol Solver (D-10); parser success alone is not
semantic resolution (M1b). False positives cost more than false negatives
(hard rule 2a): when in doubt, a rule stays silent or reports INCONCLUSIVE.

L3's `PSEUDO_TESTED_METHOD` (M5, `docs/rules/PSEUDO_TESTED_METHOD.md`) and
`SUBSUMED_TEST` (M4, `docs/rules/SUBSUMED_TEST.md`, D-61) do **not** follow
this contract: both anchor evidence from a real PIT mutation run (not AST
traversal) rather than a single AST-traversed test method - the former on a
production method, the latter on a *pair* of test methods - and both have
their own fixture-free specs. The fingerprint definition below is shared
across all three rule families; everything else on this page is
L0-specific.

## Test method recognition

A method is a test method if annotated with any of (fully resolved):
JUnit 5 `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`,
`@TestTemplate`; JUnit 4 `org.junit.Test` (D-24). `@Disabled`/`@Ignore`
methods are still analyzed but findings carry a `disabled test` note in the
message. Lifecycle methods (`@BeforeEach` etc.) are not test methods but are
included in helper traversal (below).

## Recognized oracle APIs (the allowlist, D-24)

A call is an oracle if it resolves to one of:

- **JUnit 5** `org.junit.jupiter.api.Assertions.*` (all `assert*`, `fail`),
  `assertThrows`, `assertDoesNotThrow`, `assertAll`.
- **JUnit 4** `org.junit.Assert.*` (all `assert*`, `fail`),
  `@Test(expected = ...)`, `org.junit.rules.ExpectedException` usage.
- **AssertJ** `org.assertj.core.api.Assertions.assertThatXxx(...)` and
  `org.assertj.core.api.BDDAssertions.thenXxx(...)` chains (`assertThat`,
  `assertThatThrownBy`, `assertThatNullPointerException`, `then`, `thenCode`,
  ... - matched by prefix, `assertThat`/`then` respectively, since both
  classes declare a whole family of typed-subject entry points sharing that
  prefix, D-34) — the chain itself counts as an oracle only if a terminal
  assertion method is invoked on it (a bare `assertThat(x);` is not an
  oracle); `Assertions.fail` (unconditional, no chain needed).
- **Hamcrest** `org.hamcrest.MatcherAssert.assertThat`.
- **Google Truth** `com.google.common.truth.Truth.assertThat(...)`/
  `assertWithMessage(...).that(...)` chains - same chain-terminal-required
  semantics as AssertJ (D-31).
- **Mockito** `verify`, `verifyNoInteractions`, `verifyNoMoreInteractions`,
  `InOrder.verify`, BDD `then(...).should(...)`.
- **Custom oracles** from configuration: `customOracles` is a list of
  `fully.qualified.Type#methodPattern` entries (glob `*` allowed in the
  method part). A resolved call matching an entry is an oracle.

Helper traversal: oracle search covers the test method body plus private or
static methods of the same compilation unit it calls (transitively, within
the unit; D-33). Calls that leave the compilation unit are not traversed in
v0.1; if unresolved or external and oracle-suggestively named, they trigger
the inconclusive path below. A bare (unqualified) call whose own resolution
fails - typically because one of the callee's parameter types is itself an
external, unconfigured type - still resolves to a same-file helper when
exactly one private or static method in the unit matches its name; real
ambiguity (more than one match) leaves it unresolved rather than guessing
(D-33).

## Confidence (D-17) — detector certainty, separate from severity

- **HIGH** — every invoked symbol relevant to the rule resolved, and the
  rule's condition is met on resolved evidence alone.
- **MEDIUM** — some irrelevant symbols unresolved, or the rule fires on a
  pattern with a known benign look-alike (each rule spec names them).
- **INCONCLUSIVE** — an unresolved or non-traversed call matches the
  oracle-suggestive name pattern
  (`assert*`, `verify*`, `check*`, `expect*`, `should*`, `require*`, `fail*`)
  or a `customOracles` pattern that could not be resolved. An INCONCLUSIVE
  finding states what could not be resolved. Unresolved evidence can never
  produce HIGH (D-17).
- LOW is reserved in the schema; no v0.1 rule emits it.

## Severity

`WARNING` for `NO_RECOGNIZED_ORACLE`, `TAUTOLOGICAL_ORACLE`,
`CATCH_ORACLE_WITHOUT_FAIL`; `INFO` (advisory) for `NULL_CHECK_ONLY`.
No v0.1 rule suggests deletion at any confidence (D-08, hard rule 3).

## Suppression

Configuration-only in v0.1 (no inline markers):
`suppressions: [{rule, pathGlob, testMethodPattern?, reason}]` — `reason` is
mandatory. Suppressed findings are counted in a `suppressedFindings` warning
so they are visible without being listed. Baselines and changed-only gating
are M3 (pre-CI) work.

## Fingerprint

`sha256(ruleId + " " + moduleId + " " + repoRelativePath +
" " + anchorSignature)`, first 16 hex chars, lowercase. Shared with L3
(`dev.coverdict.analysis.model.Fingerprint`, M5): the anchor is a fully
qualified test method signature for these four L0 rules, a fully qualified
production method signature for `PSEUDO_TESTED_METHOD`. Line numbers are
excluded, so unrelated edits and moves don't change identity. One finding
per (rule, anchor): if a rule matches the same anchor more than once,
occurrences merge into one finding spanning the first occurrence's lines.

## Fixture convention

Fixtures live in `fixtures/rules/<RULE_ID>/` as Java sources:
`Positive.java` (rule fires), `Negative.java` (rule must not fire),
`Unresolved.java` (missing symbol → INCONCLUSIVE/MEDIUM/silent as each rule
specifies). Positive/Negative compile against the fixture harness classpath
(JUnit 4+5, AssertJ, Mockito, Hamcrest); Unresolved files parse but
deliberately reference a symbol absent from the classpath — they must never
be "fixed" to compile.
Expectations are machine-readable header comments, one per test method:

```java
// expect: finding method=<name> confidence=<HIGH|MEDIUM|INCONCLUSIVE>
// expect: none method=<name>
```

M1b implements each rule against these fixtures; a rule is done only when
every fixture expectation passes and precision measurement (M1c criterion 4)
is wired.
