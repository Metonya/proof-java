<#
.SYNOPSIS
  Overall-scope sonar-compatible parity check (M1c criterion 2, overall only -
  see docs/M0-VALIDATION-MANIFEST.md for the Community Edition branch/PR
  analysis limitation on new-code parity).

.PARAMETER Name
  Phase name (e.g. gson).
.PARAMETER RepoRoot
  Full path to the repo checkout to run the scanner from.
  Single-module phases (gson, assertj): pass the module's own directory
  directly and a one-element -ModuleDirs @(".") - sonar-maven-plugin's
  :sonar goal fails with "Maven session does not declare a top level
  project" when combined with reactor -pl on this setup, so single-module
  runs must NOT use -pl; the scanner runs from inside the module itself.
.PARAMETER ModuleDirs
  RepoRoot-relative directories of the modules to bind into ONE sonar
  project (D-37: dropwizard's real multi-module reactor). More than one
  entry switches to a reactor scan (`-pl <dirs> -am sonar:sonar` from
  RepoRoot) so Sonar aggregates coverage across modules into a single
  project, matching what coverdict's own merged `overall` metric spans.
  Sonar attributes each JaCoCo XML's lines to whichever module's source
  tree they fall under - sonar.sources/sonar.tests are intentionally NOT
  passed in multi-module mode; each submodule's own Maven layout supplies
  them. One entry keeps the pre-D-37 single-module behavior (explicit
  sonar.sources=src/main/java / sonar.tests=src/test/java, no -pl).
.PARAMETER JacocoXmlRelativePaths
  Path(s) to jacoco.xml, RepoRoot-relative, same order as ModuleDirs.
.PARAMETER CoverdictOverallPercent
  coverdict's own overall.sonar-compatible.percent from a --no-vcs run
  (read by the caller from verdict-no-vcs.json and passed in) - the merged
  figure across every bound module, compared against Sonar's single
  aggregated project here.
.PARAMETER OutDir
  Directory to write <name>/sonar-parity.md into.
.PARAMETER Scanner
  "Maven" (default) invokes sonar-maven-plugin and needs a pom.xml.
  "Cli" invokes the standalone `sonar-scanner`, which needs no build tool at
  all - it takes sonar.sources/sonar.tests/the JaCoCo XML directly. That is
  what makes a Gradle corpus repo measurable (D-45): D-36 deferred phase 3's
  parity as "a Gradle equivalent needs its own design", but coverdict already
  consumes source roots and a report path explicitly (D-01/D-02), so the
  generic scanner matches its input model exactly and no Gradle-specific
  design was needed after all.
.PARAMETER SourcesRelative / TestsRelative
  Cli scanner only: sonar.sources / sonar.tests, relative to RepoRoot.
#>
param(
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string[]]$ModuleDirs,
    [Parameter(Mandatory)][string[]]$JacocoXmlRelativePaths,
    [Parameter(Mandatory)][double]$CoverdictOverallPercent,
    [Parameter(Mandatory)][string]$OutDir,
    [string]$SonarHost = "http://localhost:9001",
    [ValidateSet("Maven", "Cli")][string]$Scanner = "Maven",
    [string]$SourcesRelative,
    [string]$TestsRelative
)

if ($Scanner -eq "Cli" -and (-not $SourcesRelative -or -not $TestsRelative)) {
    throw "-SourcesRelative and -TestsRelative are required when -Scanner Cli"
}

if ($ModuleDirs.Count -ne $JacocoXmlRelativePaths.Count) {
    throw "-ModuleDirs ($($ModuleDirs.Count)) and -JacocoXmlRelativePaths ($($JacocoXmlRelativePaths.Count)) must have the same count"
}

$projectKey = "coverdict-corpus-$Name"
$xmlPathsArg = $JacocoXmlRelativePaths -join ","

Push-Location $RepoRoot
try {
    if ($Scanner -eq "Cli") {
        # Build-tool-agnostic path: the standalone scanner takes the same
        # three facts coverdict itself takes (sources, tests, JaCoCo XML), so
        # a Gradle repo needs no pom and no Gradle plugin wiring.
        & sonar-scanner `
            "-Dsonar.host.url=$SonarHost" `
            "-Dsonar.token=$env:SONAR_TOKEN" `
            "-Dsonar.projectKey=$projectKey" `
            "-Dsonar.projectName=$projectKey" `
            "-Dsonar.projectBaseDir=$RepoRoot" `
            "-Dsonar.sources=$SourcesRelative" `
            "-Dsonar.tests=$TestsRelative" `
            "-Dsonar.java.binaries=." `
            "-Dsonar.coverage.jacoco.xmlReportPaths=$xmlPathsArg"
        if ($LASTEXITCODE -ne 0) { throw "sonar-scanner failed with exit $LASTEXITCODE" }
    } elseif ($ModuleDirs.Count -eq 1) {
        Push-Location (Join-Path $RepoRoot $ModuleDirs[0])
        try {
            & mvn -q -B `
                org.sonarsource.scanner.maven:sonar-maven-plugin:sonar `
                "-Dsonar.host.url=$SonarHost" `
                "-Dsonar.token=$env:SONAR_TOKEN" `
                "-Dsonar.projectKey=$projectKey" `
                "-Dsonar.projectName=$projectKey" `
                "-Dsonar.coverage.jacoco.xmlReportPaths=$xmlPathsArg" `
                "-Dsonar.sources=src/main/java" `
                "-Dsonar.tests=src/test/java"
            if ($LASTEXITCODE -ne 0) { throw "sonar-maven-plugin failed with exit $LASTEXITCODE" }
        } finally {
            Pop-Location
        }
    } else {
        # Reactor scan: sonar-maven-plugin's native multi-module support
        # aggregates every -pl module into one project (aggregate lines
        # attributed by path inside each XML - no per-module sonar.sources
        # override needed, each submodule's own pom supplies its layout).
        $modulePl = $ModuleDirs -join ","
        & mvn -q -B -pl $modulePl -am `
            org.sonarsource.scanner.maven:sonar-maven-plugin:sonar `
            "-Dsonar.host.url=$SonarHost" `
            "-Dsonar.token=$env:SONAR_TOKEN" `
            "-Dsonar.projectKey=$projectKey" `
            "-Dsonar.projectName=$projectKey" `
            "-Dsonar.coverage.jacoco.xmlReportPaths=$xmlPathsArg"
        if ($LASTEXITCODE -ne 0) { throw "sonar-maven-plugin failed with exit $LASTEXITCODE (if this is the known '-pl + sonar:sonar' top-level-project error, retry per-module and sum manually, or drop -am if all bound modules' deps are already installed)" }
    }
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

Modules bound into this one Sonar project: $($ModuleDirs -join ', ')

| Source | Metric | Value |
|---|---|---|
| coverdict (--no-vcs, overall.sonar-compatible, merged across bound modules) | percent | $CoverdictOverallPercent |
| SonarQube ($SonarHost, project $projectKey) | coverage | $sonarCoverage |
| SonarQube (bonus cross-check) | line_coverage | $sonarLineCoverage |

Delta (sonar-compatible vs SonarQube coverage): $delta (threshold +/-0.1)

**Result: $(if ($pass) { "PASS" } else { "FAIL" })**
"@

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$report | Out-File -Encoding utf8 (Join-Path $OutDir "sonar-parity.md")
Write-Host $report
