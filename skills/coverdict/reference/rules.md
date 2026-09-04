# The six rules — what fires, what to do, where it misfires

Authoritative spec: `docs/rules/<RULE>.md` in the proof-java repo. This file is
the acting guide; the spec wins on any disagreement.

Confidence is `HIGH` / `MEDIUM` / `INCONCLUSIVE`. `LOW` exists in the schema but
no rule emits it in v0.1. **`INCONCLUSIVE` means the detector was uncertain** —
surface it to the human, do not act on it as if it were true.

**No rule at any confidence suggests deleting a test.** If you are about to
delete or disable a test because of a finding, you have misread the finding.

---

## `NO_RECOGNIZED_ORACLE` — WARNING (L0)

No allowlisted assertion/verification call, and no expected-exception construct,
in the test method body or its same-compilation-unit helpers.

**Fix by:** adding an assertion on the observed behavior.

**Known false-positive shapes — waive, do not weaken the test:**
- The real assertion lives in a helper in **another file**. proof-java only
  traverses same-compilation-unit helpers by design (D-17). Register the helper
  via `customOracles` in `proof.config.json`
  (`fully.qualified.Type#methodPattern`, glob `*` allowed).
- The assertion library is not on the allowlist. Recognized: JUnit 4 & 5,
  AssertJ, Mockito verification, Hamcrest, Google Truth, JUnit 4
  `ExpectedException`. Anything else needs `customOracles`.
- Known unrecognized AssertJ shapes: `WithAssertions` interface delegation,
  `InstanceOfAssertFactory` chains, `SoftAssertions` instance receivers
  (`softly.assertThat(x)`).
- A library owning only the **terminal** link of a chain rooted in the test's own
  helper (e.g. JUnit Platform Testkit's `Events.assertEventsMatchExactly`).

**Never fires on** `@TestFactory` methods — dynamic-test oracles live in lambdas
proof-java does not traverse in v0.1. This is a documented blind spot, not a pass.

---

## `TAUTOLOGICAL_ORACLE` — WARNING (L0)

The assertion's outcome cannot depend on the code under test; it holds for any
implementation.

**Fix by:** asserting on a value actually produced by the code under test.

The firing pattern list is **closed and exhaustive by design** — anything outside
it does not fire, so a false positive here is unlikely but a false *negative* is
expected. Never fires on identical operands where either side contains a method
call (side effects are not statically provable), nor on assertions about the code
under test's own public constants.

---

## `CATCH_ORACLE_WITHOUT_FAIL` — WARNING (L0)

A try/catch shape where, if the code under test throws, every oracle is skipped
and the test still passes.

**Fix by:** `assertThrows`/`assertDoesNotThrow`, adding `fail(...)` after the
invocation inside the `try`, or letting the exception propagate.

**Never fires on:** `try`-with-resources with no `catch`; a `catch` that rethrows
or calls `fail`; or an oracle placed **after the whole `try` statement**. If you
see it fire on one of these shapes, that is a real bug — record it, do not
restructure working code to appease it.

---

## `NULL_CHECK_ONLY` — INFO, advisory (L0)

Every oracle in the test only checks non-nullness; content is never verified.

**Fix by:** asserting on the value's content.

This is a weak test, not a broken one. **Acceptable residue** — INFO severity
exists precisely so this does not block. Never fires on mixed tests (one real
content assertion is enough), nor when the test's subject genuinely is
nullability.

---

## `PSEUDO_TESTED_METHOD` — WARNING (L3, needs `--mutation-report`)

A production method is covered — some test executes it — but **every mutant
generated for it survived**. Nothing observes what the method does. Anchors on a
production method (`finding.productionMethod`), not a test method.

**Fix by:** adding an assertion on the method's return value or observable side
effect, in at least one covering test. **Never by adding more coverage** —
coverage is already there; observation is what is missing.

**Never fires on:** a method with zero mutants generated; or a method any of
whose mutants is `NO_COVERAGE` (that is a coverage gap, not a pseudo-tested
finding). A mix of `SURVIVED` and `KILLED` never fires either — at least one test
does verify it.

**Known limit (D-56):** the mutator set is gregor `RETURNS` + `VOID_METHOD_CALLS`,
an approximation of Descartes' extreme mutation. `RETURNS` mutators replace the
return *value*, not the method body, so side effects before a `return` still run;
and no gregor mutator approximates extreme mutation for a `void` method.
Confidence is `HIGH` only when every surviving mutant is `RETURNS`-family,
`MEDIUM` when `VOID_METHOD_CALLS` is mixed in.

---

## `SUBSUMED_TEST` — INFO (L3, needs `--mutation-report`)

One test's mutant kill-set is a **strict subset** of another's: under the mutators
this run exercised, the narrower test kills nothing the richer one does not.
Carries two identities: `testMethod`/`path` (the subsumed test) and
`relatedTestMethod`/`relatedPath` (the dominator).

**This is not a deletion candidate.** Hard rule 2: a subset is never the
suspicious one — if anything, look at the **superset** (eager) test. Review
whether both are needed; if intentionally redundant (characterization vs.
regression), document why both exist.

**Never fires on:** a test with an empty kill-set (the empty set is a subset of
everything), nor on an essential test (one that solely kills some mutant).

**Known limit (D-61):** the narrow mutator set likely **overstates** redundancy —
a richer mutator set would show fewer tests as subsumed. The finding is always
scoped to "under the mutators this run exercised", never an unqualified
redundancy verdict.
