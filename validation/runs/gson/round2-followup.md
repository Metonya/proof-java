# gson canary — round-2 backlog follow-up (D-33)

Not a new calibration round (D-32 already passed at 94.3%) - a verification
that the two round-2 backlog gaps are actually closed. Same checkout, same
`jacoco.xml`, freshly rebuilt `coverdict.jar`. Full output:
`round2-followup-verdict-base.json`.

## Root cause correction

The plan going in assumed the `CircularReferenceTest` gap was about lambda
**arguments** specifically. A minimal JavaParser reproduction (real
`junit.jar`, `ReflectionTypeSolver`) disproved that before any code changed:

| Case | Shape | Result |
|---|---|---|
| A | JDK-only functional interface (`Runnable`) + lambda argument | resolves fine |
| B | external/undeclared parameter type + plain (non-lambda) argument | fails |
| C | external/undeclared parameter type + lambda argument | fails |

B and C fail identically - the lambda is irrelevant. The real cause: to
report a resolved call, JavaParser's Symbol Solver must also resolve the
**callee's own declaration**, including every parameter type; if any
parameter type is itself unresolvable (an external library type with no
jar/`--classpath` configured), resolution of the *call site* fails too,
independent of what the argument expression looks like. `ThrowingRunnable`
(`org.junit.jupiter.api.function.ThrowingRunnable`) is exactly such a type
in a `--classpath`-less real run. D-33 documents this corrected
understanding; the fix ships against the real cause, not the original
lambda hypothesis.

## The fix

`OracleRecognizer.resolve()` gained one more fallback, after import-
anchoring: for a **bare** (unqualified) call whose own resolution failed,
search the same compilation unit for a **private or static** method whose
simple name matches. Exactly one match resolves it (`SOLVED` tier); more
than one, or a qualified call, is left unresolved rather than guessed
(D-33). The existing private-only helper-traversal condition was also
broadened to private-**or-static** (the other round-2 gap), sharing the
same `isLocalHelperCandidate` predicate.

## Result

| | Round 2 (D-32) | Round-2 follow-up (D-33) |
|---|---|---|
| Total findings | 38 | 33 |
| HIGH | 35 | 33 |
| INCONCLUSIVE | 3 | 0 |
| HIGH-tier precision (of what was reviewed) | 94.3% (33/35) | **100% (33/33)** |

All 5 previously-flagged findings are accounted for:

- `DefaultTypeAdaptersTest#testDefaultDateDeserialization` (line 444) and
  `DefaultTypeAdaptersTest#testNullSerialization` (line 217) - both
  same-file public-static helper cases - **no longer flagged.**
- `CircularReferenceTest#testCircularSerialization`,
  `#testSelfReferenceArrayFieldSerialization`,
  `#testSelfReferenceCustomHandlerSerialization` - all three
  `assertThrowsStackOverflow(() -> ...)` cases - **no longer flagged**
  (the same-file name-match fallback resolves the call and finds the real
  `assertThrows`/Truth oracle inside).
- `SqlTypesGsonTest#testNullSerializationAndDeserialization` (line 54) -
  **still flagged, correctly.** Its own private helper calls
  `DefaultTypeAdaptersTest.testNullSerializationAndDeserialization(gson, c)`
  - a *cross-file* call, a different boundary than either fix touched
  (D-17: custom/external oracle helpers need explicit configuration). This
  was already labeled a true positive in round 2 and stays one.

## Regression check

`mvn -pl coverdict-cli -am test`: 153/153 green, including the two new
`fixtures/rules/NO_RECOGNIZED_ORACLE/Negative.java` cases added for this
fix (`oracleInPublicStaticHelper`, `oracleReachedThroughUnresolvableParamTypeHelper`).
`python schema/validate-goldens.py`: all three goldens pass.
