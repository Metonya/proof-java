# Loop run — recording template

A loop that leaves no trace cannot be evaluated later. Record every run under
`validation/runs/loop/<repo>-NN/`. This is deliberately separate from
`validation/runs/<repo>/`, which holds **precision** runs: those label whether a
finding was *true*; these label whether a finding was *useful*.

Files to produce:

```
loop-log.md          narrative, protocol, verbatim commands (numbered blocks)
doctor-round0.txt    preflight output, verbatim
iterations.csv       one row per analyze invocation
actions.csv          one row per (finding, iteration)
verdict-iter-N.json  the raw outputs
loop-summary.md      the readable conclusion
```

## `iterations.csv`

```csv
iteration,layer,command_ref,exit_code,wall_clock_sec,findings_total,findings_new,findings_resolved,findings_carried,newcode_jacoco_line,newcode_strict_line,newcode_sonar_compatible,overall_jacoco_line
```

`layer` ∈ `L0L1 | L2 | L3`. `command_ref` points at a numbered command block in
`loop-log.md` rather than inlining a 400-character command per row. Leave a metric
cell empty when the run produced no number — never write `0` for "unavailable".

## `actions.csv`

```csv
iteration,rule,confidence,path,startLine,testMethod,fingerprint,agent_action,useful,disposition,human_review,notes
```

- `agent_action` ∈ `added_oracle | strengthened_oracle | added_test | added_coverage | config_suppression | no_action`
- `useful` ∈ `yes | no | unclear` — **did the finding change what you did?**
- `disposition` ∈ `accepted | waived | deferred | false_positive`
- `human_review` ∈ `yes | no` — did a human have to step in?

Record the row **before** acting on the finding, not after. `fingerprint` is what
joins a finding across iterations.

## `loop-summary.md`

Mirrors the house style of `validation/runs/<repo>/precision-summary.md`:

```markdown
# Loop run <NN> — <repo> @ <commit>

Target class(es), their measured pre-run coverage, build tool, JDK, caps used.

## Result
Findings encountered / acted on / waived / false-positive.
Finding-response rate, useful-finding rate.
Iterations to converge per target. Total wall clock, split L0+L1 vs L3.

## What the loop actually produced
The concrete test changes coverdict caused that would not have happened
otherwise. **If the answer is "none", say so plainly** — that is the single
most important result this artifact can carry.

## Where coverdict was wrong, slow, or useless
False positives, preflight friction, anything that could not be acted on.
Bugs found here are recorded, not fixed — fixing mid-loop is what turned six
earlier dogfood rounds into debugging sessions with zero workflow evidence.

## Kill-criterion reading
ROADMAP: "If dogfood users do not repeat the workflow or findings are
predominantly ignored/waived, stop integration work and revisit the product
wedge." State, with the numbers, whether that holds — and whether the workflow
was voluntarily repeated.

## Limits of this run
A self-run loop on your own repository measures the mechanism, not demand
(M0-PERSONA.md states this limit for proxy repos; it applies here too).

## Recommendation (not implemented in this session — out of scope)
Feeds ROADMAP backlog one-liners.
```

## Caps

State them before starting, and treat hitting one as a **recorded outcome**, not
a failure to hide: default 3 analyze iterations per target class, 60 minutes wall
clock per target.
