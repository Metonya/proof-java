# NULL_CHECK_ONLY

**Severity INFO — advisory.** Every oracle in the test only checks
non-nullness; the content of the values is never verified. Advisory: this is
a weak test, not a broken one, and it never suggests deletion (ROADMAP M1b).

## Fires when

A recognized test method has at least one oracle, and **every** oracle in
the traversal is one of:

- JUnit 4/5 `assertNotNull(...)`
- AssertJ `assertThat(...).isNotNull()` (as the only terminal call in the
  chain)
- Hamcrest `assertThat(x, notNullValue())` / `is(notNullValue())`

## Confidence

- **HIGH** — all oracles resolved and all are null checks.
- **MEDIUM** — all resolved oracles are null checks but the traversal also
  contains unresolved non-oracle-named calls (a hidden oracle may exist).
- **INCONCLUSIVE** — never emitted: with an unresolved oracle-suggestive
  call present, the "only null checks" premise is unprovable and the rule
  stays silent.

## Never fires on

- Any test containing at least one non-null-check oracle (mixed tests are
  fine).
- Tests whose subject genuinely is nullability (name matching
  `*NotNull*`/`*NonNull*` does **not** exempt — the check is behavioral,
  not name-based; a null-contract test still deserves the advisory since
  content assertions elsewhere would strengthen it. The INFO severity keeps
  this non-noisy).

## Suggested action text

"The only verification is non-nullness; consider asserting on the value's
content. Advisory."
