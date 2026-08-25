# PSEUDO_TESTED_METHOD

**Severity WARNING.** A production method is covered - a test executes it -
but every mutant generated for it survived: no test observes what the
method actually does. L3 evidence (M5, D-56); unlike the four L0 rules in
this directory, this rule does not follow `README.md`'s shared contract
(see the note there) - it anchors on a *production* method via a real PIT
run, not on a test method via AST traversal.

## Fires when

`--mutation-report` collected at least one mutant for the method, and:

- No mutant has status `NO_COVERAGE` (the method is fully covered by the
  diff-scoped mutation run - a partially covered method is silently
  excluded rather than guessed at, hard rule 3a), **and**
- Every mutant has status `SURVIVED`.

A mix of `SURVIVED` and `KILLED` never fires: at least one test does verify
the method. A mix that includes `NO_COVERAGE` never fires either - that is
a coverage gap, not a pseudo-tested finding, and produces no warning of its
own (it is the normal, expected shape of a diff-scoped run whose mutants
land partly outside what changed-code coverage reaches).

## Inconclusive statuses

If any mutant for the method has status `TIMED_OUT`, `MEMORY_ERROR`,
`RUN_ERROR`, `NON_VIABLE`, `NOT_STARTED`, or `STARTED`, the rule stays
silent for that method **and** emits a `MUTATION_INCONCLUSIVE_STATUS`
warning naming it - never silently treated as either killed or survived
(hard rule 3a).

## Confidence

- **HIGH** — every mutant's `mutator` is one of PIT's gregor `RETURNS`
  family (`EMPTY_RETURNS`, `FALSE_RETURNS`, `NULL_RETURNS`,
  `PRIMITIVE_RETURNS`, `TRUE_RETURNS`) - the closer approximation of
  Descartes' extreme mutation (D-56, `docs/RESEARCH.md` §14).
- **MEDIUM** — at least one surviving mutant is `VOID_METHOD_CALLS`, a
  weaker approximation (it only removes void calls made from within the
  mutated method, not a whole-body replacement, and approximates nothing
  for a `void` method's own extreme mutation).
- **LOW**/**INCONCLUSIVE** — never emitted by this rule (mutants in a
  non-final status take the separate warning path above instead of a
  low-confidence finding).

## Never fires on

- A method with zero mutants generated for it (nothing to evaluate).
- A method any of whose mutants is `NO_COVERAGE` (partial coverage, not
  evaluated).
- A method any of whose mutants is in a non-final status (evaluation
  deferred, `MUTATION_INCONCLUSIVE_STATUS` warning instead).
- A mutated class that cannot be resolved back to a changed source file's
  repo-relative path (a defensive case that should not occur in practice,
  since mutation targets come from the same changed-file set the path
  index is built from).

## Anchor and fingerprint

`productionMethod` (schema `finding.productionMethod`, not `testMethod`)
carries `<FQCN>#<methodName><methodDescriptor>` - the JVM method
descriptor disambiguates overloads, unlike the four L0 rules' human-
readable parameter-type signature. `startLine`/`endLine` span the
method's surviving mutants' line numbers. The fingerprint
(`dev.coverdict.analysis.model.Fingerprint`, shared with the L0 rules)
anchors on this same production-method signature instead of a test
method's.

## Suggested action text

"Add an assertion on this method's return value or observable side effect
for at least one covering test." Never suggests deletion at any
confidence (hard rule 3, D-08) - a surviving mutant is a review candidate,
not proof the method or its tests are worthless (equivalent-mutant
possibility, same caution D-21 applies to coverage identity).

## Known limits (see `docs/RESEARCH.md` §14)

- gregor's `RETURNS` mutators change only a method's return value, not its
  body - a side effect before the `return` still executes under a mutant
  where Descartes' whole-body replacement would not.
- No gregor mutator meaningfully approximates extreme mutation for a
  `void` method.
- Precision against a labeled corpus is unmeasured as of M5's initial
  ship - `docs/ROADMAP.md`'s M5 kill criterion (two dogfood repos,
  manually verified precision under 90%) is the pending validation.
