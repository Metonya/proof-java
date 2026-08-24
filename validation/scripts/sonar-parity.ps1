<#
.SYNOPSIS
  Overall-scope sonar-compatible parity check (M1c criterion 2, overall only -
  see docs/M0-VALIDATION-MANIFEST.md for the Community Edition branch/PR
  analysis limitation on new-code parity).

.PARAMETER Name
  Phase name (e.g. gson).
.PARAMETER ModuleDir
  Full path to the Maven module directory to run the scanner from (the
  scanner is invoked from inside this directory, not via reactor -pl -
  sonar-maven-plugin's :sonar goal fails with "Maven session does not
  declare a top level project" when combined with -pl on this setup).
.PARAMETER JacocoXmlRelativePath
  Path to jacoco.xml relative to ModuleDir.
.PARAMETER CoverdictOverallPercent
  coverdict's own overall.sonar-compatible.percent from a --no-vcs run
  (read by the caller from verdict-no-vcs.json and passed in).
.PARAMETER OutDir
  Directory to write <name>/sonar-parity.md into.
#>
param(
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$ModuleDir,
    [Parameter(Mandatory)][string]$JacocoXmlRelativePath,
    [Parameter(Mandatory)][double]$CoverdictOverallPercent,
    [Parameter(Mandatory)][string]$OutDir,
    [string]$SonarHost = "http://localhost:9001"
)

$projectKey = "coverdict-corpus-$Name"

Push-Location $ModuleDir
try {
    & mvn -q -B `
        org.sonarsource.scanner.maven:sonar-maven-plugin:sonar `
        "-Dsonar.host.url=$SonarHost" `
        "-Dsonar.token=$env:SONAR_TOKEN" `
        "-Dsonar.projectKey=$projectKey" `
        "-Dsonar.projectName=$projectKey" `
        "-Dsonar.coverage.jacoco.xmlReportPaths=$JacocoXmlRelativePath" `
        "-Dsonar.sources=src/main/java" `
        "-Dsonar.tests=src/test/java"
    if ($LASTEXITCODE -ne 0) { throw "sonar-maven-plugin failed with exit $LASTEXITCODE" }
} finally {
    Pop-Location
}

$headers = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($env:SONAR_TOKEN):")) }

# Sonar processes an uploaded report asynchronously (Compute Engine); a
# fixed short sleep works for a small project (gson) but a larger one
# (assertj: ~4600 test files) can still be IN_PROGRESS well past 5s, which
# silently returns empty measures rather than an error. Poll the CE task
# instead of guessing a sleep duration.
$ceUrl = "$SonarHost/api/ce/component?component=$projectKey"
for ($i = 0; $i -lt 60; $i++) {
    $ce = Invoke-RestMethod -Uri $ceUrl -Headers $headers
    if (-not $ce.queue -or $ce.queue.Count -eq 0) { break }
    Start-Sleep -Seconds 5
}

$measuresUrl = "$SonarHost/api/measures/component?component=$projectKey&metricKeys=coverage,line_coverage,lines_to_cover,uncovered_lines"
$resp = Invoke-RestMethod -Uri $measuresUrl -Headers $headers

$sonarCoverage = ($resp.component.measures | Where-Object { $_.metric -eq "coverage" }).value
$sonarLineCoverage = ($resp.component.measures | Where-Object { $_.metric -eq "line_coverage" }).value

$delta = [Math]::Abs([double]$sonarCoverage - $CoverdictOverallPercent)
$pass = $delta -le 0.1

$report = @"
# $Name - sonar-compatible parity (overall scope, M1c criterion 2)

Community Edition instance: branch/PR analysis unsupported, so this checks
**overall** scope only; new-code parity remains open (documented limit,
see docs/M0-VALIDATION-MANIFEST.md).

| Source | Metric | Value |
|---|---|---|
| coverdict (--no-vcs, overall.sonar-compatible) | percent | $CoverdictOverallPercent |
| SonarQube ($SonarHost, project $projectKey) | coverage | $sonarCoverage |
| SonarQube (bonus cross-check) | line_coverage | $sonarLineCoverage |

Delta (sonar-compatible vs SonarQube coverage): $delta (threshold +/-0.1)

**Result: $(if ($pass) { "PASS" } else { "FAIL" })**
"@

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$report | Out-File -Encoding utf8 (Join-Path $OutDir "sonar-parity.md")
Write-Host $report
