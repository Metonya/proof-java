# gson - sonar-compatible parity (overall scope, M1c criterion 2)

Community Edition instance: branch/PR analysis unsupported, so this checks
**overall** scope only; new-code parity remains open (documented limit,
see docs/M0-VALIDATION-MANIFEST.md).

| Source | Metric | Value |
|---|---|---|
| coverdict (--no-vcs, overall.sonar-compatible) | percent | 91 |
| SonarQube (http://localhost:9001, project coverdict-corpus-gson) | coverage | 91.0 |
| SonarQube (bonus cross-check) | line_coverage | 92.4 |

Delta (sonar-compatible vs SonarQube coverage): 0 (threshold +/-0.1)

**Result: PASS**
