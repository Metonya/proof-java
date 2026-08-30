# Loop run 01 — coverdict @ 7ca1075

The first end-to-end execution of the canonical workflow in `docs/M0-PERSONA.md`
(2026-08-29). Target: `dev.coverdict.analysis.subprocess.SubprocessWorkspace`,
39.1% LINE coverage at the start. Build: Maven, single module, JDK 17 / JDK 25.
Caps: 3 analyze iterations, 60 min — neither was hit. Protocol and verbatim
commands: `loop-log.md`. Response labels: `actions.csv`. Cost: `iterations.csv`.

## Result

| | |
|---|---|
| Findings encountered (distinct fingerprints) | 6 |
| Acted on | 1 (the only HIGH / WARNING one) |
| Deferred | 5 (all `SUBSUMED_TEST`, INFO) |
| Waived / false-positive | 0 |
| **Useful-finding rate** | 2/6 — one changed what the agent did, one correctly reported the consequence |
| Iterations to converge | 2 analyze runs after the first verdict |
| Wall clock | ~2 min of analysis (1s + 11s + 13s + 39s) + 3 × 18s builds |
| L0+L1 vs L3 cost | 1s vs 11–39s per run |
| Human review needed | 0 findings |

The agent wrote 9 test methods across two rounds (8 → 17 in the class, 370 → 379
repo-wide), all green.

## What the loop actually produced

**One real, non-obvious test improvement that no coverage tool would have asked
for.**

`destroyProcessTree` ends with an unconditional `process.destroyForcibly()`. The
agent's first test asserted only that the immediate child died — true on both
sides of the `if (isWindows())` branch, so both mutants survived. The Windows
`taskkill /F /T` branch exists for exactly one reason: killing **descendants**,
because `destroyForcibly()` alone leaves PIT's grandchild minions orphaned
(D-58/D-59). That branch was entirely unverified.

coverdict flagged it as `PSEUDO_TESTED_METHOD` (HIGH). The agent diagnosed the
cause itself, added a test that spawns a child-with-grandchild and asserts the
grandchild dies, and **empirically probed** that `destroyForcibly()` alone leaves
the grandchild alive before trusting the fix. It also predicted correctly that
the `isWindows() → true` mutant is an equivalent mutant on Windows that no honest
test can kill — and declined to write one for it.

**The decisive number: LINE coverage did not move. 76.1% before, 76.1% after.**
A coverage gate would have scored this change as zero. The branch went from
executed-but-unobserved to observed.

A second true positive followed as a direct consequence: the now-redundant
child-only test was correctly reported as `SUBSUMED_TEST`, naming the narrower
test and its dominator, with no deletion suggested (hard rule 2). Both were kept
— the grandchild test is `@EnabledOnOs(WINDOWS)` by necessity.

## Where coverdict was wrong, slow, or useless

**1. A silent false green under JDK 25 — a hard-rule-3a gap.** The identical L3
command was run twice, changing only the JDK:

| | JDK 25 | JDK 17 |
|---|---|---|
| production mutant statuses | 12 × `NO_COVERAGE` (100%) | 7 `KILLED`, 3 `SURVIVED`, 2 `NO_COVERAGE` |
| findings | **0** | **5** |
| `analysis.status` / exit | `complete` / `0` | `complete` / `0` |
| L3-related warning | **none** | — |

PIT 1.15.8's bundled ASM cannot read **the JDK's own** class files under Java 25
(`Unsupported class file major version 69`, thrown from
`ComputeClassWriter.getCommonSuperClass` during frame computation). The project's
own bytecode is major 61 (Java 17) and reads fine — verified directly on both the
production and test `.class` files. The failure is a property of **the JDK the
analysis runs on**, not of the target's bytecode.

This sharpens D-53/D-54, which framed the ASM ceiling as a property of the target
repo. It is also the more dangerous shape: coverdict reported `complete`, exit 0,
zero findings, and **no warning naming the problem**. D-64's
`MUTATION_EMPTY_EVIDENCE` does not fire, because the evidence was not empty — 26
methods and 60 mutants were resolved. It was present and worthless. An agent
following `skills/coverdict/SKILL.md` would have reported "mutation testing found
nothing", which was false. Evidence: `diag-jdk25-mutation.log` (1 occurrence of
the ASM error) vs `diag-jdk17-mutation.log` (0 occurrences).

**2. `newCode` is inert when an agent only writes tests.** In every iteration
`coverage.newCode` was `0/0`, `percent: null`, in all three modes — the only
changed file is a test file, correctly classified `excluded` (D-27). This is not
a defect, but it means the entire L1 half of the tool has nothing to say in the
"write tests for existing code" branch of `M0-PERSONA.md` step 1. Anyone building
a CI gate on `newCode` needs to know that branch exists.

**3. Convergence is reported more strongly than it holds.** The
`PSEUDO_TESTED_METHOD` finding disappeared because one mutant became `TIMED_OUT`
(non-final status → rule skips the method), not because it was killed. The
correct reading is "converted into a `MUTATION_INCONCLUSIVE_STATUS` warning", but
an agent reading `findings[]` alone sees a clean resolution.

**4. Minor: scratch-file warning noise.** 8 of 9 warnings in iteration 1 were
`UNTRACKED_NON_JAVA_FILE` from the loop's own working directory.

**5. `SUBSUMED_TEST` was low-value at this scale.** Four of five were deferred as
`unclear`: with only 12 production mutants, subsumption is weakly evidenced, which
is precisely the limit D-61 documents. None was wrong; none changed a decision.

Nothing in this list was fixed. That is deliberate: fixing mid-loop is what turned
all six earlier WTA dogfood rounds into debugging sessions that produced zero
workflow evidence.

## Kill-criterion reading

> *"If dogfood users do not repeat the workflow or findings are predominantly
> ignored/waived, stop integration work and revisit the product wedge."*

For the first time this is answerable with numbers rather than being
unfalsifiable.

- **Ignored/waived: no.** 0 of 6 findings were waived or judged false positives.
  The single HIGH/WARNING finding was acted on and produced a real improvement.
  The five deferred findings are all INFO-severity `SUBSUMED_TEST`, whose own rule
  doc says they are review prompts, not defects. **Findings were not predominantly
  ignored.**
- **Repeat the workflow: yes, within this run** — the loop was voluntarily run a
  second time after the first verdict, and converged. Across *sessions* it is
  still unmeasured; that needs run 02.

**The criterion does not trigger.** Caveat, stated rather than buried: one run, on
the maintainer's own repository, by the tool's own author. This measures the
mechanism, not demand — the same limit `M0-PERSONA.md` records for its proxy
repos. A self-run loop cannot produce evidence of organic demand and this
document does not claim it does.

## Limits of this run

- Single class, single repo, single session.
- The loop agent worked inside a repo containing `AGENTS.md`, which discusses
  oracles and evidence at length. A real developer here would have it too, so this
  is the honest condition — but the agent cannot be called naive about test
  quality. A repo without such a document would be a cleaner measurement.
- The agent was careful (it reported a test it could not write rather than faking
  it, and probed its own fix). A sloppier agent would make coverdict look better.
  This was the harder case, which is the right way to be wrong.
- Windows only. The `isWindows()` equivalent mutant and the `@EnabledOnOs` test
  are both platform-shaped; a POSIX run would produce different mutant statuses.

## Recommendation (not implemented in this session — out of scope)

1. **Warn when an L3/L2 target class resolves only `NO_COVERAGE` mutants.**
   `MUTATION_EMPTY_EVIDENCE` covers zero records; nothing covers "records present,
   all of them `NO_COVERAGE` for every targeted production class". This is the
   false-green above and is the highest-value item here.
2. **Detect the runtime-JDK/ASM mismatch in `doctor`** — comparing the running
   JVM's feature version against PIT's ASM ceiling is a cheap preflight check, and
   `doctor` is exactly where the six WTA rounds' setup lessons already live.
3. Consider recording D-67-style prose as a real DECISIONS entry; D-67 is cited in
   three places and has no entry.
4. Run 02 on a corpus repo (gson / commons-lang) to get a repeat signal across
   sessions and outside the maintainer's own code.
