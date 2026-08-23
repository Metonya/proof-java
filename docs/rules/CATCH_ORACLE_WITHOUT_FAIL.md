# CATCH_ORACLE_WITHOUT_FAIL

**Severity WARNING.** A try/catch shape in a test that lets the
exception path pass silently: if the code under test throws, every oracle is
skipped and the test still passes.

## Fires when

A test method contains a `try` statement where all of:

1. The `try` block invokes at least one method (the code under test).
2. Every recognized oracle in the method occurs **inside the `try` block
   after that invocation** (or there is no oracle at all — then
   `NO_RECOGNIZED_ORACLE` yields to this rule; only one of the two fires,
   this one, as it is more specific).
3. The `catch` block(s) contain no recognized oracle, no `fail(...)`, no
   rethrow (`throw`), and no call matching the oracle-suggestive pattern.

## Confidence

- **HIGH** — all invocations in try and catch resolved; catch is empty or
  only logs (resolved calls to logging/printing APIs).
- **MEDIUM** — catch contains resolved non-oracle calls other than logging
  (a comment-worthy but recognizable pattern).
- **INCONCLUSIVE** — catch contains an unresolved call, or one matching the
  oracle-suggestive name pattern (it may be a custom fail helper).

## Never fires on

- `try`-with-resources without a `catch` clause.
- A `catch` that rethrows (wrapped or not) or calls `fail`.
- Oracles present after the whole `try` statement (the exception would still
  skip them, but a `finally`/post-try oracle at least executes on the
  non-throwing path and asserts observed state — a weaker but real oracle;
  flagging it produces too many false positives, documented limit).

## Suggested action text

"An exception thrown here passes silently; add fail() after the invocation
inside try, use assertThrows/assertDoesNotThrow, or let the exception
propagate."
