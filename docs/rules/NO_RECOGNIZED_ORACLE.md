# NO_RECOGNIZED_ORACLE

**Severity WARNING.** A test method contains no recognized oracle: no
allowlisted assertion/verification call (README allowlist), no expected
exception construct, in the method body or its same-unit private helpers.

## Fires when

All of the following hold for a recognized test method:

1. No resolved call in the traversal matches the oracle allowlist or a
   `customOracles` entry.
2. No `@Test(expected=...)`, no `assertThrows`/`assertThatThrownBy`-family
   usage, no `ExpectedException` rule interaction.
3. The method body is not empty (an empty test body is a different smell and
   out of v0.1 scope — stays silent).

## Confidence

- **HIGH** — every invoked method in the traversal resolved; none is an
  oracle; none matches the oracle-suggestive name pattern.
- **MEDIUM** — one or more calls unresolved or external, but none matches
  the oracle-suggestive name pattern or any `customOracles` pattern.
- **INCONCLUSIVE** — an unresolved/external call matches the
  oracle-suggestive name pattern or an unresolvable `customOracles` entry;
  the message names that call.

## Never fires on

- `@TestFactory` methods (dynamic tests carry oracles in lambdas coverdict
  cannot safely traverse in v0.1 — always skipped, listed as a documented
  limit, not INCONCLUSIVE noise).
- Methods whose only statements delegate to a resolved allowlisted oracle
  wrapper (e.g. `assertAll(...)`).

## Suggested action text

"Add an assertion on the observed behavior, or register the helper as a
custom oracle in configuration." Never suggests deletion.
