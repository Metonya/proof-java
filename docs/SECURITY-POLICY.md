# Untrusted-input policy (M0 deliverable 6)

Threat model: coverdict parses files it did not produce — JaCoCo XML, git
output, Java sources, configuration — from repositories that may contain
adversarial content (e.g. a malicious PR an agent is asked to review). The
CLI runs locally with the user's privileges. The policy goal: no parsed input
can execute code, read files outside the analysis inputs, make the process
hang unboundedly, or inject content into coverdict's own output.

## 1. Secure XML parsing

- JAXP secure processing ON; DTDs rejected outright (any `<!DOCTYPE` is a
  structured parse failure, not a warning), external general/parameter
  entities disabled, XInclude off, no schema/DTD fetching.
- Applies to every XML input (JaCoCo reports now; PIT/Descartes reports later).
- Obligated negative test: an XXE fixture must yield a structured failure and
  exit 2/3 — never a file read, never a network attempt (M1c criterion 7).

## 2. Resource limits

- Report size cap: 256 MB per XML file by default, configurable; exceeding it
  is a structured failure, not silent truncation.
- Findings cap: 10,000 findings per run; beyond that, analysis stops with an
  explicit truncation warning in the JSON (`status` reflects it) — a capped
  run is never presented as a complete clean run.
- Parsing is streaming where the engine allows; no unbounded in-memory DOM of
  attacker-sized input.

## 3. Safe process invocation

- External processes (git; later PIT/Descartes) are invoked as argv arrays,
  never through a shell; no string interpolation of user or repo content into
  a command line; `--` separates options from paths where the tool supports it.
- Explicit working directory; minimal environment. On Windows no `cmd.exe`
  indirection.
- Process output is treated as data (see §4), and hung subprocesses are
  bounded by a timeout that produces a structured failure.

## 4. Path handling and output escaping

- All reported paths are normalized repo-relative with forward slashes.
- Report-to-source mapping rejects any resolved path escaping the repository
  root (`..`, absolute injection, symlink escape) as unmapped — which is a
  structured incomplete result per hard rule 3a, never a silent drop.
- JSON output is produced only by a serializer, never string concatenation.
- Terminal output escapes control characters from any input-derived string
  (file names, module ids), preventing terminal escape-sequence injection.

## 5. No network, no telemetry

- The CLI makes zero network calls: no update checks, no crash reporting, no
  telemetry. Offline operation is the only mode.
- Any future opt-in network feature requires a DECISIONS entry and remains
  off by default.

## 6. Enforcement

Every clause above maps to at least one automated negative test in M1c
criterion 7; a clause without a failing-input test is treated as unimplemented.
