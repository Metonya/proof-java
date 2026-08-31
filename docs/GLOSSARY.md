# Glossary — HTML report labels

D-80. Every machine code that can surface in the HTML report (`coverdict
analyze --html-report`, `coverdict render-html`) gets one Turkish friendly
name and a one-sentence explanation, defined once in
[`ReportLabels.java`](../coverdict-cli/src/main/java/dev/coverdict/analysis/report/ReportLabels.java).
This page is the prose mirror of that map — read it to understand *why* a
label reads the way it does; read `ReportLabels.java` for the exact current
text, since this page is not auto-generated and can lag by one edit.

The code is never replaced by its friendly name, only accompanied (hard rule
5): the report always shows `Doğrulamasız test` next to
`NO_RECOGNIZED_ORACLE`, `<code>`-formatted, never one without the other. This
keeps a report grep-able by the same rule id used in `--suppress`,
`docs/rules/`, and the JSON output, while staying readable without knowing
the codes by heart.

`ReportLabelsTest` fails the build if a code that actually appears in a
report (a rule id, an `AnalysisReason` this codebase constructs, a
`Severity`/`Confidence`/`Classification` value, a PIT mutant status, or a
coverage metric mode) has no entry here. An unlabeled code is not a crash —
`HtmlRenderer` falls back to the raw code — but it is a regression the test
exists to catch before a report ships with a wall of bare identifiers again.

## Rule ids (L0/L3 findings, `docs/rules/`)

| Code | Turkish label | What it means |
|---|---|---|
| `NO_RECOGNIZED_ORACLE` | Doğrulamasız test | No recognized assertion, verification, or expected exception — the test runs green without checking anything. |
| `TAUTOLOGICAL_ORACLE` | Kendini doğrulayan test | The assertion's outcome cannot depend on the code under test (constant-vs-constant, self-comparison, literal boolean). |
| `CATCH_ORACLE_WITHOUT_FAIL` | fail() içermeyen catch bloğu | Every oracle sits inside a `try` block after the call under test; a thrown exception skips them all and the test still passes. |
| `NULL_CHECK_ONLY` | Yalnızca null kontrolü | Every oracle in the test only checks non-nullness; content is never verified. Advisory, not a defect. |
| `PSEUDO_TESTED_METHOD` | Sözde test edilmiş metot | A method is covered (a test runs it) but every mutant generated for it survived — no test observes its actual behavior. |
| `SUBSUMED_TEST` | Kapsanan test | This test's mutant kill-set is a strict subset of another test's; the broader test is the suspicious one (hard rule 2). |

## Analysis reason codes (warnings / incomplete reasons)

Only the codes this codebase actually constructs via `new AnalysisReason(...)`
are labeled — not the larger set of string constants that appear only inside
exception messages or `coverdict doctor` output, which never reach the HTML
report. See `ANALYSIS_REASON_CODES` in `ReportLabelsTest.java` for the
authoritative list; new codes need both a `ReportLabels` entry and a row
added there.

Grouped by what part of the pipeline they come from:

- **Diff / changed-file resolution** — `CHANGED_FILES_EXCLUDED`,
  `CHANGED_JAVA_OUTSIDE_MODULES`, `CHANGED_LINES_ABSENT_FROM_REPORT`,
  `REPORT_MISSING_CHANGED_FILE`, `UNTRACKED_JAVA_FILE`,
  `UNTRACKED_NON_JAVA_FILE`.
- **Classpath / input problems** — `CLASSPATH_ENTRY_UNUSABLE`,
  `CLASSPATH_FILE_UNREADABLE`, `CLASSPATH_TOO_LARGE`,
  `MUTATION_CLASSPATH_MISSING`, `PER_TEST_CLASSPATH_MISSING`.
- **Coverage / module gaps** — `MISSING_SOURCE_FILE`, `MODULE_WITHOUT_REPORT`.
- **L2 (test-based evidence)** — `PER_TEST_COLLECTION_FAILED`,
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

Every one of these is prefixed, in the report, with a reminder that a
warning is not a code defect — it narrows what the numbers above it cover
(hard rule 3a: unknown/skipped evidence is reported, never silently folded
into a "clean" result).

## Severity / confidence / classification

These are existing enums (`Severity`, `Confidence`,
`Classification#schemaValue()`) — the labels only make their already-fixed
values readable in prose:

| Code | Label |
|---|---|
| `INFO` | Bilgi |
| `WARNING` | Uyarı |
| `HIGH` / `MEDIUM` / `LOW` / `INCONCLUSIVE` | Yüksek / Orta / Düşük / Belirsiz güven |
| `mapped` / `excluded` / `non-executable` / `unsupported` / `unknown` | Eşleşti / Hariç tutuldu / Çalıştırılamaz / Desteklenmiyor / Bilinmiyor |

## Mutant statuses (PIT)

Standard PIT outcomes, all nine covered even though only a handful show up
in most real runs — `KILLED`, `SURVIVED`, `NO_COVERAGE`, `TIMED_OUT`,
`NON_VIABLE`, `MEMORY_ERROR`, `RUN_ERROR`, `STARTED`, `NOT_STARTED`. The
report groups counts by status explicitly (D-80 fixed the earlier "diğer"
catch-all bucket that hid whether 12 mutants were `NO_COVERAGE` or a mix of
five different failure modes — see D-79/D-80 history for that report's
Bulgular/Mutasyon complaints).

## Coverage metric modes

| Code | Label | Definition (hard rule 5) |
|---|---|---|
| `jacoco-line` | Satır kapsama (JaCoCo) | Matches JaCoCo's own LINE counter exactly. |
| `strict-line` | Tam satır kapsama | A line counts only if every instruction on it ran — stricter than `jacoco-line`. |
| `sonar-compatible` | Satır + dal kapsama (Sonar) | Matches the SonarQube UI within ±0.1 on the same analysis scope. |

## Adding a new code

1. Add the `ReportLabels.entry(...)` line (name + one honest sentence, no
   marketing language).
2. Add a row to this file.
3. If it is a rule id, it must already be in `RuleIds.ALL` — the same test
   that checks label coverage also checks that `ALL` matches every constant
   declared on the class.
4. Run `ReportLabelsTest` — it fails loudly (assertion message names the
   missing code) if a label is missing.
