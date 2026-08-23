# Teamscale (CQSE) — capability and pricing deep-dive

Sourced by an external research pass (Aug 2026), reviewed and lightly
corrected by the coverdict maintainers before filing. Distilled into
RESEARCH.md §7a; read this file only for full citations or formula detail.

## Review notes (coverdict team)

- The two formula images in the original report did not render as text.
  They were decoded and transcribed below; verify against
  `https://docs.teamscale.com/faq/` and
  `https://docs.teamscale.com/reference/ui/testgaps/` before quoting them
  externally.
- The original report's summary matrix filled a "per-test coverage /
  redundancy" column for StrykerJS and Stryker.NET with text describing
  *execution architecture* ("worker thread isolation", "perTest coverage
  analysis for mutant-run skipping"), not actual duplicate/redundant-test
  detection. Neither tool flags tests as behaviorally redundant; both use
  per-test coverage only to skip irrelevant mutants for speed. Treat that
  matrix column as **not evidence of an Axis-E capability**.
- A few citations in the original report look mismatched (e.g. a
  `pytest-gremlins` repo cited for a cosmic-ray execution-model claim). This
  file keeps the original citation list as delivered; re-verify any specific
  number before treating it as confirmed.

## 1. Pricing and licensing structure

Per-contributor commercial subscription (on-prem or cloud SaaS); no public
list price — gated behind a sales quote. Free permanent license for academic
use; free secondary on-prem instances (staging/shadow) if the combined
contributor count across instances stays within the contracted threshold.

Contributor count, exact formula (rolling 180-day window):

```
Licensed Contributors = max( U_active,180d , C_analyzed,180d )
```

where `U_active,180d` = named accounts that logged into the web UI or used
the REST API in the last 180 days, and `C_analyzed,180d` = unique developers
who committed to a path Teamscale is configured to analyze in the last 180
days. It is a **max of two counts, not a union of two sets** — confirm this
reading against the FAQ page before citing it as settled.

Deduplication: same-email commits merge; transposed name orders
("Jane Doe" / "Doe, Jane") auto-merge; manual aliases supported; CI bots and
default system accounts (e.g. a merge bot, SAP's `DDIC`/`SAP*`) are excluded
by default; service accounts can be set to "access key only" to drop off the
seat count while keeping API access.

Source: `https://docs.teamscale.com/faq/`.

## 2. Testwise coverage architecture (`teamscale-java-profiler`)

Open-source JVM agent (Gradle multi-module, Java 21 toolchain targeting
Java 8), embeds JaCoCo for bytecode instrumentation. Per-test attribution
comes from explicit lifecycle signals, not automatic detection: the test
runner sends `POST /test/start/{uniformPath}` before a test and
`POST /test/end/{uniformPath}` after, with result status
(PASSED/FAILURE/ERROR/SKIPPED/IGNORED), duration, and stack trace in the
payload. `test/start` clears the active probe registers — the same
process-global-state approach RESEARCH.md §2/§9 already documents as
JaCoCo's core limitation; Teamscale does not escape it, it just automates
the reset trigger via explicit start/end calls instead of sequential test
ordering (contrast with D-13's approach).

Four output modes (`tia-mode`): `teamscale-upload` (in-memory, streams to a
server), `exec-file` (writes standard JaCoCo `.exec` to disk), `disk`
(writes per-test JSON to disk), `http` (returns JSON in the HTTP response).
**`exec-file` and `disk` modes run fully offline, no server required**, and
the repo ships an offline `bin/convert` CLI to turn `.exec`/JSON into
XML — this is more standalone-usable than RESEARCH.md's prior "gated behind
the platform" framing assumed; only the *TGA/Pareto/diff-coverage analysis*
requires the server, not the raw per-test capture.

Documented limitations: duplicate non-identical classes across modules
produce warnings and imprecise/unmapped coverage; parallel Gradle builds
need explicit per-task property scoping to avoid state leak. Single-JVM
parallel test threads, async/thread-pool context propagation, and
`@BeforeEach`/`@AfterEach`/class-level fixture attribution are **not
documented** either way — same open question D-18 already flags for our own
sequential-run design.

Source: `https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/`,
`https://github.com/cqse/teamscale-java-profiler/blob/master/CLAUDE.md`.

## 3. Pareto Testing / test minimization

Three combined heuristics, not one:

1. **Coverage-based Pareto optimization** — orders tests by marginal method
   coverage added per unit of execution time, from historical testwise
   coverage + runtime.
2. **Similarity scoring** — tokenizes modified-source identifiers/strings,
   strips language stop-words, ranks tests by vector-space term proximity to
   the diff when coverage data is missing.
3. **AI test clustering** — embeds test source via a configured LLM provider
   (OpenAI, Gemini, or self-hosted Ollama) and clusters structurally similar
   tests for diversity ranking, independent of coverage history.

No documentation ties mutation-kill signal into this ranking. Output is a
prioritized order plus an execution-time budget slider — **never an
automatic deletion suggestion**; CQSE's own docs say human inspection is
required before removing a low-ranked test.

Note for VISION.md/AGENTS.md hard rule 1 ("never ask an LLM whether a test
is good and store the answer as a finding"): heuristic 3 is exactly the
thing coverdict's evidence-over-judgment principle rules out. This is a
genuine, citable point of philosophical contrast, not just a feature gap.

Source: `https://docs.teamscale.com/reference/ui/test-suggestions/`.

## 4. Static test-quality analysis

Confirmed: test clone/duplicate-code detection across test files is a real,
documented feature (this is genuine Axis-E-adjacent capability, unlike the
Stryker mislabeling noted above). Weak-oracle detection (assertion-less
`@Test` methods) and tautological-assertion detection are **not documented**
in Teamscale's primary static-analysis rule listing — treat as absent for
positioning purposes, but worded as "undocumented," not "confirmed absent,"
per our own D-17 standard for how we phrase our own findings.

Source: `https://docs.teamscale.com/reference/supported-technologies/static-analysis/`,
`https://docs.teamscale.com/introduction/best-practices/`.

## 5. Mutation testing

Confirmed absent. Teamscale does not integrate PIT, Descartes, or a native
mutation engine. Change verification relies on Test Gap Analysis (below),
not fault injection.

## 6. Diff / new-code coverage — Test Gap Analysis (TGA)

Method-level, not line-level (line coverage exists in the source-code view,
but TGA's ratio is computed per-method). Exact formula:

```
Test Gap Ratio = (M_untested,new + M_untested,changed)
                 / (M_total,new + M_total,changed) × 100%
```

`M_*,new`/`M_*,changed` are methods added/modified between a chosen baseline
commit and the target commit; unchanged methods are excluded from both
numerator and denominator. **TGA runs entirely server-side** — it requires
parsing the VCS commit graph, AST diffing to separate behavioral changes
from refactors, and mapping execution data against the diff, all inside
Teamscale's server. The standalone profiler and `teamscale-build`/
`teamscale-upload` CLIs only collect and transmit raw data; they cannot
compute a TGA ratio offline.

Source: `https://docs.teamscale.com/reference/test-gap-analysis/`,
`https://docs.teamscale.com/reference/ui/testgaps/`.

## 7. Deployment and offline boundary

On-prem (zip/Docker/native service/AWS image) or cloud SaaS. Split exactly
along the line already drawn in §2: the profiler agent runs fully offline;
every intelligence feature (TGA, Pareto, similarity scoring, diff-coverage
computation, PR quality gates) requires a running server. No CLI-only path
computes these without a server backend.

## 8. Profiler licensing and interoperability

`teamscale-java-profiler` source is public on GitHub; its exact SPDX license
header was **not confirmed** from the primary doc snippets reviewed — check
the repository's `LICENSE` file directly before stating a license in
RESEARCH.md/DECISIONS.md. It emits standard JaCoCo `.exec` files and an open
JSON testwise schema (test names, results, durations, covered line ranges).
coverdict ingesting either format is not itself a licensing problem, but do
not copy any of the profiler's or server's source.

## Cited sources (as delivered)

https://docs.teamscale.com/faq/ ·
https://docs.teamscale.com/getting-started/installing-teamscale/ ·
https://github.com/cqse/teamscale-java-profiler/blob/master/CLAUDE.md ·
https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/testwise-coverage-recording/ ·
https://docs.teamscale.com/reference/coverage-profilers/teamscale-java-profiler/troubleshooting/ ·
https://docs.teamscale.com/reference/ui/test-suggestions/ ·
https://docs.teamscale.com/reference/test-gap-analysis/ ·
https://docs.teamscale.com/reference/ui/testgaps/ ·
https://docs.teamscale.com/reference/supported-technologies/static-analysis/ ·
https://docs.teamscale.com/introduction/best-practices/ ·
https://docs.teamscale.com/reference/upload-formats-and-samples/testwise-coverage/ ·
https://teamscale.com/features/test-selection ·
https://teamscale.com/for-testers ·
https://github.com/cqse/teamscale-jacoco-agent
