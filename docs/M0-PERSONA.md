# M0 deliverable 1 — Primary persona, canonical workflow, dogfood repositories

Decisions taken with the maintainer on 2026-08-23.

## Primary persona

**An AI coding agent operating in a developer's local session** (Claude Code or
equivalent), generating or repairing tests on a Java repository under human
supervision. Chosen over VISION's other two candidates (Java team pre-push
developer; PR-receiving maintainer) because it is the only persona whose
canonical workflow we can observe end-to-end today with the resources at hand.
The other two remain secondary audiences — deprioritized, not removed.

## Canonical pre-push workflow

1. Agent changes production code and/or writes tests.
2. Agent runs the repo's build with tests and JaCoCo XML output
   (e.g. `mvn verify` with the JaCoCo plugin bound).
3. Agent invokes the coverdict CLI: repository, JaCoCo XML report(s), and
   either a base-ref diff or working-tree mode.
4. coverdict emits verdict JSON — changed-code coverage in the three metric
   modes plus L0 oracle findings — or a structured incomplete result.
5. Agent acts on findings (adds missing coverage, strengthens weak oracles,
   or surfaces them to the human), repeating 2–4 until the verdict is clean
   or the human accepts the residue; only then is the work committed/pushed.

The defining property: the loop completes before commit/push. No server-side
tool is in the loop.

**Current workaround** (what the persona does today without coverdict): the
agent reads raw JaCoCo HTML/XML itself — token-expensive, format-fragile, and
with no oracle-quality signal — or skips verification entirely. That skip is
exactly the failure mode VISION describes.

**Setup tolerance:** one command after clone. The persona is agent-driven, so
anything scriptable is acceptable in principle, but the supervising human's
patience budget is minutes, not hours, and no server component is tolerated.

## Dogfood repositories (public proxies)

The maintainer has no in-house Java codebase suitable for daily dogfooding, so
three public repositories act as proxies: on each, an AI agent is asked to add
or repair tests for selected classes and coverdict critiques the result.
Limit acknowledged: proxies exercise the workflow's mechanics; they do not
measure organic demand. That remains a hypothesis per VISION.

| Repo | Ref @ pinned commit | Build | Why this one |
|---|---|---|---|
| `apache/commons-lang` | `master` @ `4d91b28fce7317132360aa9f3bbf31cc6e479f00` | Maven, single module | Mature JUnit 5 suite (~4,500 tests); the well-tested baseline an agent extends |
| `google/gson` | `main` @ `dae37cf0fe12235b76fb09f01118a0a8c8823f42` | Maven, small multi-module | JUnit 4-heavy suite — exercises the JUnit 4 half of D-24; reflection-heavy code |
| `dropwizard/dropwizard` | `release/4.0.x` @ `87940b9728fa6cce6598a0433da97ace373ee828` | Maven, multi-module | Forces report-to-module binding and multi-module paths (the earlier "mixed JUnit 4+5" claim was verified false at this pin - D-38) |

Buildability at the pinned commits is verified by the validation-manifest
spot-check (M0 deliverable 5), not asserted here. Dropwizard also appears in
the M1c validation corpus; the purposes differ (dogfood = observe the
workflow; validation = correctness at pinned commits) and the overlap is fine.

## O-04 resolution

All three dogfood repositories are Maven, as is the M1c validation corpus's
multi-module member. O-04 is resolved **Maven-first** — see D-23 in
DECISIONS.md. M1's CLI remains build-tool-agnostic (D-01/D-02); this decides
only which build plugin M3 ships first.
