# assertj - sonar-compatible parity (overall scope, M1c criterion 2)

Community Edition instance: branch/PR analysis unsupported, so this checks
**overall** scope only; new-code parity remains open (documented limit,
see docs/M0-VALIDATION-MANIFEST.md).

Note: the first `sonar-parity.ps1` run against this project returned empty
measures (script queried the API before SonarQube's Compute Engine finished
processing the report - assertj-core's ~4600 test files take longer than
gson's fixed 5s sleep). Polled `api/ce/component` directly instead of
re-running the scan; the script itself is now fixed to poll rather than
guess a sleep duration.

| Source | Metric | Value |
|---|---|---|
| coverdict (--no-vcs, overall.sonar-compatible) | percent | 74.4 |
| SonarQube (http://localhost:9001, project coverdict-corpus-assertj) | coverage | 74.4 |
| SonarQube (bonus cross-check) | line_coverage | 77.7 |
| SonarQube (bonus cross-check) | lines_to_cover | 19508 (matches coverdict's own denominator exactly) |

Delta (sonar-compatible vs SonarQube coverage): 0.0 (threshold +/-0.1)

**Result: PASS**
