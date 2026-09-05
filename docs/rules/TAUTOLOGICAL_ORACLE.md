# TAUTOLOGICAL_ORACLE

**Severity WARNING.** An oracle whose outcome cannot depend on the code under
test — it passes (or fails) for any implementation.

## Fires when — closed pattern list (v0.1)

An allowlisted assertion call matches one of:

1. **Constant vs constant** — both comparison operands are compile-time
   constants (literals, final constants resolved to literals, or
   constant-folded expressions): `assertEquals(2, 1 + 1)`.
2. **Literal boolean** — `assertTrue(true)`, `assertFalse(false)`, and the
   AssertJ/Hamcrest equivalents on literal booleans.
3. **Self-comparison** — both operands are syntactically identical
   expressions containing no method calls: `assertEquals(x, x)`.
4. **Fresh-object null check** — `assertNotNull(new X(...))` (a freshly
   constructed object can never be null).

Patterns are exhaustive by design; anything else does not fire. Extending the
list requires updating this spec and its fixtures first.

## Confidence

- **HIGH** — patterns 1, 2, 4, and pattern 3 when operands are simple names
  or field accesses.
- **MEDIUM** — pattern 3 with identical compound expressions (array access,
  casts); still call-free, but aliasing look-alikes exist.
- **INCONCLUSIVE** — never emitted by this rule: if constants/operands can't
  be resolved, the rule stays silent (a wrong tautology claim is the most
  trust-burning false positive this tool can make).

## Never fires on

- Identical operands where either side contains any method call (side
  effects unprovable statically).
- Assertions on constants that are the code under test's own public
  constants (`assertEquals(3.14159, MathLib.PI)` is a legitimate contract
  pin) — operand resolving to a non-test-class field does not count as a
  plain constant.
- Pattern 3 (self-comparison) when the test method or its enclosing class
  carries `@SuppressWarnings("EqualsWithItself")` (D-98) — the standard
  IDE/static-analysis marker for a deliberate `equals()` reflexivity check
  (`x.equals(x)` must hold per the `Object.equals` contract), found as a real
  false positive on apache/commons-io's `ByteOrderMarkTest`. Scoped to
  pattern 3 only: the same suppression does not silence patterns 1, 2, or 4.

## Suggested action text

"This assertion holds for any implementation; assert on a value produced by
the code under test."
