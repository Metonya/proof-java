# Glossary — terminology and report labels

Two things live here: the words this tool uses for its own concepts, and the
friendly name attached to every machine code that can appear in the HTML report
(`coverdict analyze --html-report`, `coverdict render-html`).

## Terminology

| Term | What it means here |
|---|---|
| **Oracle** | The part of a test that decides pass or fail — an assertion, a `verify(...)`, an expected exception. A test without an oracle executes code but checks nothing, so it stays green no matter what the code does. |
| **Verdict** | The tool's output document: the numbers, the findings, and everything the run could *not* establish. Never a single score. |
| **Evidence level (L0-L3)** | How much evidence a finding rests on. L0 reads test sources, L1 adds coverage and the diff, L2 adds per-test coverage, L3 adds mutation results. A higher level costs more time and proves more. |
| **Mutant** | A small deliberate change to production code (flip a comparison, drop a call) made by PIT. If a test fails on the changed code, the mutant is *killed* — the test really observes that behaviour. If every test still passes, it *survived*. |
| **Pseudo-tested method** | A method that tests execute but no test actually observes: every mutant of it survived. Coverage counts it; nothing verifies it. |
| **Subsumed test** | A test whose killed-mutant set is contained in another test's. It adds no evidence the broader test does not already provide. |
| **Changed code / newCode** | The lines the diff touched. Most of the tool's answers are scoped to these, not the whole repository. |
| **Ambient line** | A production line that every test executes the same way (framework setup, static init). It appears in per-test evidence but does not distinguish one test from another. |

## How a report label works

Every code that can surface in the report gets one friendly name and one
sentence, defined once in
[`ReportLabels.java`](../coverdict-cli/src/main/java/dev/coverdict/analysis/report/ReportLabels.java).
This page is the prose mirror of that map; read `ReportLabels.java` for the exact
current text, since this page is not generated and can lag by one edit.

The code is never replaced by its friendly name, only accompanied (hard rule 5):
the report always shows `Test with no assertion` next to `NO_RECOGNIZED_ORACLE`,
`<code>`-formatted, never one without the other. That keeps a report greppable by
the same rule id used in `--suppress`, in `docs/rules/`, and in the JSON output,
while staying readable without knowing the codes by heart.

`ReportLabelsTest` fails the build if a code that can actually appear in a report
(a rule id, an `AnalysisReason` this codebase constructs, a
`Severity`/`Confidence`/`Classification` value, a PIT mutant status, or a coverage
metric mode) has no entry. An unlabeled code is not a crash — `HtmlRenderer` falls
back to the raw code — but it is the regression that test exists to catch.

## Rule ids (L0/L3 findings, `docs/rules/`)

| Code | Label | What it means |
|---|---|---|
| `NO_RECOGNIZED_ORACLE` | Test with no assertion | No recognized assertion, verification, or expected exception — the test runs green without checking anything. |
| `TAUTOLOGICAL_ORACLE` | Self-proving test | The assertion's outcome cannot depend on the code under test (constant-vs-constant, self-comparison, literal boolean). |
| `CATCH_ORACLE_WITHOUT_FAIL` | catch block without fail() | Every oracle sits inside a `try` block after the call under test; a thrown exception skips them all and the test still passes. |
| `NULL_CHECK_ONLY` | Null check only | Every oracle in the test only checks non-nullness; content is never verified. Advisory, not a defect. |
| `PSEUDO_TESTED_METHOD` | Pseudo-tested method | A method is covered (a test runs it) but every mutant generated for it survived — no test observes its actual behaviour. |
| `SUBSUMED_TEST` | Subsumed test | This test's mutant kill-set is a strict subset of another test's; the broader test is the suspicious one (hard rule 2). |

## Analysis reason codes (warnings / incomplete reasons)

These are the tool's "why is this number not the whole story" messages. Only the
codes this codebase actually constructs via `new AnalysisReason(...)` are labeled
— not the larger set of string constants that appear only inside exception
messages or `coverdict doctor` output, which never reach the HTML report. See
`ANALYSIS_REASON_CODES` in `ReportLabelsTest.java` for the authoritative list; a
new code needs both a `ReportLabels` entry and a row there.

Grouped by the part of the pipeline they come from:

- **Diff / changed-file resolution** — `CHANGED_FILES_EXCLUDED`,
  `CHANGED_JAVA_OUTSIDE_MODULES`, `CHANGED_LINES_ABSENT_FROM_REPORT`,
  `REPORT_MISSING_CHANGED_FILE`, `UNTRACKED_JAVA_FILE`,
  `UNTRACKED_NON_JAVA_FILE`.
- **Classpath / input problems** — `CLASSPATH_ENTRY_UNUSABLE`,
  `CLASSPATH_FILE_UNREADABLE`, `CLASSPATH_TOO_LARGE`,
  `MUTATION_CLASSPATH_MISSING`, `PER_TEST_CLASSPATH_MISSING`.
- **Coverage / module gaps** — `MISSING_SOURCE_FILE`, `MODULE_WITHOUT_REPORT`.
- **L2 (per-test evidence)** — `PER_TEST_COLLECTION_FAILED`,
  `PER_TEST_EMPTY_EVIDENCE`, `PER_TEST_NO_CHANGED_TARGETS`,
  `PER_TEST_TARGET_NOT_BOUND`, `PER_TEST_TARGET_UNRESOLVED`,
  `PER_TEST_TRUNCATED`.
- **L3 (mutation evidence)** — `MUTATION_EMPTY_EVIDENCE`,
  `MUTATION_INCONCLUSIVE_STATUS`, `MUTATION_NO_CHANGED_TARGETS`,
  `MUTATION_TARGET_NOT_BOUND`, `MUTATION_TARGET_UNRESOLVED`,
  `MUTATION_TRUNCATED`.
- **Output-size limits** — `FINDINGS_TRUNCATED`.
- **L0 parsing** — `UNPARSEABLE_TEST_SOURCE`.
- **User configuration** — `SUPPRESSED_FINDINGS`.

Every one of these is prefixed, in the report, with a reminder that a warning is
not a code defect — it narrows what the numbers above it cover (hard rule 3a:
unknown or skipped evidence is reported, never silently folded into a "clean"
result).

## Severity / confidence / classification

Existing enums (`Severity`, `Confidence`, `Classification#schemaValue()`); the
labels only make their already-fixed values readable in prose:

| Code | Label |
|---|---|
| `INFO` | Info |
| `WARNING` | Warning |
| `HIGH` / `MEDIUM` / `LOW` / `INCONCLUSIVE` | High / Medium / Low confidence, Inconclusive |
| `mapped` / `excluded` / `non-executable` / `unsupported` / `unknown` | Mapped / Excluded / Non-executable / Unsupported / Unknown |

## Mutant statuses (PIT)

Standard PIT outcomes, all nine labeled even though only a handful show up in
most runs — `KILLED`, `SURVIVED`, `NO_COVERAGE`, `TIMED_OUT`, `NON_VIABLE`,
`MEMORY_ERROR`, `RUN_ERROR`, `STARTED`, `NOT_STARTED`. The report groups counts by
status explicitly rather than folding the rare ones into an "other" bucket, which
would hide whether 12 mutants were `NO_COVERAGE` or a mix of five failure modes.

## Coverage metric modes

| Code | Label | Definition (hard rule 5) |
|---|---|---|
| `jacoco-line` | Line coverage (JaCoCo) | Matches JaCoCo's own LINE counter exactly. |
| `strict-line` | Strict line coverage | A line counts only if every instruction on it ran — stricter than `jacoco-line`. |
| `sonar-compatible` | Line + branch coverage (Sonar) | Matches the SonarQube UI within ±0.1 on the same analysis scope. The mode is named `sonar-compatible`, never bare `sonar`, because SonarQube is someone else's trademark and this tool is not affiliated with it. |

## Adding a new code

1. Add the `ReportLabels.entry(...)` line (name + one honest sentence, no
   marketing language).
2. Add a row to this file.
3. If it is a rule id, it must already be in `RuleIds.ALL` — the same test that
   checks label coverage also checks that `ALL` matches every declared constant.
4. Run `ReportLabelsTest` — it fails loudly (the assertion message names the
   missing code) if a label is missing.
