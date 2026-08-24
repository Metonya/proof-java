# dropwizard - sonar-compatible parity (overall scope, M1c criterion 2)

Community Edition instance: branch/PR analysis unsupported, so this checks
**overall** scope only; new-code parity remains open (documented limit,
see docs/M0-VALIDATION-MANIFEST.md).

Modules bound into this one Sonar project: dropwizard-util, dropwizard-validation

| Source | Metric | Value |
|---|---|---|
| coverdict (--no-vcs, overall.sonar-compatible, merged across bound modules) | percent | 81.1 |
| SonarQube (http://localhost:9001, project coverdict-corpus-dropwizard) | coverage | 81.1 |
| SonarQube (bonus cross-check) | line_coverage | 84.8 |

Delta (sonar-compatible vs SonarQube coverage): 0 (threshold +/-0.1)

**Result: PASS**
