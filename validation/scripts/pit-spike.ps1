<#
.SYNOPSIS
  M2 Faz 0 spike: proves (or disproves) that PIT's CoverageExporterFactory
  SPI can resolve block coverage to real source lines with a method-level
  test-name map, on a bounded target scope. Reproduces
  validation/runs/pit-spike/FINDINGS.md.

.DESCRIPTION
  Three things happen, in order:
    1. Compile validation/runs/pit-spike/exporter-poc/CoverdictLineExporter.java
       against the pinned PIT jars and package + install it as a throwaway
       Maven artifact (dev.coverdict.spike:pit-line-exporter:0.0.1).
    2. Add a THROWAWAY 'pit-spike' Maven profile to the target module's pom.xml
       (git-reverted at the end, never committed) declaring pitest-maven with
       the exporter jar (and, for JUnit 5 targets, pitest-junit5-plugin +
       an explicit junit-platform-launcher pin - see FINDINGS.md #5.2).
    3. Run `mvn ... -P pit-spike org.pitest:pitest-maven:1.15.8:mutationCoverage`
       scoped to -TargetClasses/-TargetTests, and report the block/line match
       count the exporter prints to stderr.

  Needs JDK 17 on PATH (PIT 1.15.8's ASM cannot read JDK 25's class file
  version - FINDINGS.md #5.1). Pass -JavaHome to override auto-detection.

.PARAMETER RepoRoot
  Root of the Maven repo/module to run against (coverdict's own repo root,
  or a corpus clone's root - e.g. C:\Users\Mert\Desktop\coverdict-corpus\gson).
.PARAMETER ModulePath
  Module directory relative to RepoRoot whose pom.xml gets the throwaway
  profile (e.g. "coverdict-cli", or "gson" for the gson corpus repo).
.PARAMETER TargetClasses
  PIT -DtargetClasses value (e.g. 'dev.coverdict.analysis.oracle.*').
.PARAMETER TargetTests
  PIT -DtargetTests value. Keep this as narrow as TargetClasses - a broad
  test scope pulls in tests unrelated to the target and PIT refuses to run
  with any red test (FINDINGS.md #5.5's gson lesson: 310 failures the first
  wide attempt, 0 once narrowed to match).
.PARAMETER ExcludedTestClasses
  Optional -DexcludedTestClasses value for tests known to fail specifically
  under PIT's own JUnit5 engine (FINDINGS.md #5.4 precedent).
.PARAMETER NeedsJUnit5Plugin
  Switch. Set for JUnit 5 target modules (adds pitest-junit5-plugin +
  junit-platform-launcher:1.12.2 pin). Omit for JUnit4-only modules (gson).
.PARAMETER JavaHome
  JDK 17 home. Default: probes the two well-known Adoptium install paths
  from windows-dev-environment memory notes.
.PARAMETER SkipExporterBuild
  Skip recompiling/reinstalling the exporter PoC jar (reuse a prior install).

.EXAMPLE
  ./pit-spike.ps1 -RepoRoot C:\Users\Mert\Desktop\coverdict `
      -ModulePath coverdict-cli `
      -TargetClasses 'dev.coverdict.analysis.oracle.*' `
      -TargetTests 'dev.coverdict.analysis.oracle.*' `
      -ExcludedTestClasses 'dev.coverdict.analysis.oracle.AllowlistCoverageGapsTest' `
      -NeedsJUnit5Plugin

.EXAMPLE
  ./pit-spike.ps1 -RepoRoot C:\Users\Mert\Desktop\coverdict-corpus\gson `
      -ModulePath gson `
      -TargetClasses 'com.google.gson.internal.LazilyParsedNumber' `
      -TargetTests 'com.google.gson.internal.LazilyParsedNumberTest'
#>
param(
  [Parameter(Mandatory = $true)][string]$RepoRoot,
  [Parameter(Mandatory = $true)][string]$ModulePath,
  [Parameter(Mandatory = $true)][string]$TargetClasses,
  [Parameter(Mandatory = $true)][string]$TargetTests,
  [string]$ExcludedTestClasses = "",
  [switch]$NeedsJUnit5Plugin,
  [string]$JavaHome = "",
  [switch]$SkipExporterBuild
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$exporterSrc = Join-Path $scriptDir "..\runs\pit-spike\exporter-poc\CoverdictLineExporter.java"
$exporterSrc = (Resolve-Path $exporterSrc).Path

if (-not $JavaHome) {
  foreach ($candidate in @(
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
  )) {
    if (Test-Path $candidate) { $JavaHome = $candidate; break }
  }
  if (-not $JavaHome) {
    throw "No JDK 17 found. Pass -JavaHome explicitly (PIT 1.15.8 cannot read JDK 25 bytecode - FINDINGS.md #5.1)."
  }
}
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

$pomPath = Join-Path $RepoRoot "$ModulePath\pom.xml"
if (-not (Test-Path $pomPath)) { throw "pom.xml not found at $pomPath" }

if (-not $SkipExporterBuild) {
  Write-Host "== Building spike exporter PoC =="
  $buildDir = Join-Path $scriptDir "..\runs\pit-spike\exporter-poc\build"
  New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
  $m2 = "$env:USERPROFILE\.m2\repository"
  $cp = "$m2\org\pitest\pitest\1.15.8\pitest-1.15.8.jar;$m2\org\pitest\pitest-entry\1.15.8\pitest-entry-1.15.8.jar"
  & javac -cp $cp -d $buildDir $exporterSrc
  if ($LASTEXITCODE -ne 0) { throw "Exporter PoC failed to compile" }
  $servicesDir = Join-Path $buildDir "META-INF\services"
  New-Item -ItemType Directory -Force -Path $servicesDir | Out-Null
  Set-Content -Path (Join-Path $servicesDir "org.pitest.coverage.CoverageExporterFactory") `
    -Value "org.pitest.coverage.export.coverdictspike.CoverdictLineExporter" -Encoding UTF8
  $jarPath = Join-Path $scriptDir "..\runs\pit-spike\exporter-poc\coverdict-spike-exporter.jar"
  Push-Location $buildDir
  & jar cf $jarPath .
  Pop-Location
  if ($LASTEXITCODE -ne 0) { throw "jar packaging failed" }
  & mvn -q org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file `
    "-Dfile=$jarPath" -DgroupId=dev.coverdict.spike -DartifactId=pit-line-exporter `
    -Dversion=0.0.1 -Dpackaging=jar
  if ($LASTEXITCODE -ne 0) { throw "install-file failed" }
}

Write-Host "== Patching THROWAWAY pit-spike profile into $pomPath =="
$original = Get-Content -Raw $pomPath
if ($original -notmatch "</profiles>") {
  throw "$pomPath has no <profiles> block - extend this script's insertion point for this repo"
}

$pluginDeps = "`n              <dependency>`n                <groupId>dev.coverdict.spike</groupId>`n                <artifactId>pit-line-exporter</artifactId>`n                <version>0.0.1</version>`n              </dependency>"
if ($NeedsJUnit5Plugin) {
  $pluginDeps = "`n              <dependency>`n                <groupId>org.junit.platform</groupId>`n                <artifactId>junit-platform-launcher</artifactId>`n                <version>1.12.2</version>`n              </dependency>`n              <dependency>`n                <groupId>org.pitest</groupId>`n                <artifactId>pitest-junit5-plugin</artifactId>`n                <version>1.2.1</version>`n              </dependency>$pluginDeps"
}

$profileBlock = @"

    <!-- THROWAWAY: coverdict M2 Faz 0 spike (validation/scripts/pit-spike.ps1). Never committed. -->
    <profile>
      <id>pit-spike</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-maven</artifactId>
            <version>1.15.8</version>
            <dependencies>$pluginDeps
            </dependencies>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
"@
$patched = $original -replace "</profiles>", $profileBlock
Set-Content -Path $pomPath -Value $patched -Encoding UTF8 -NoNewline

try {
  $classDirs = "$RepoRoot\$ModulePath\target\classes;$RepoRoot\$ModulePath\target\test-classes"
  $mvnArgs = @(
    "-q", "-B", "-pl", $ModulePath, "-am", "-P", "pit-spike",
    "test-compile",
    "org.pitest:pitest-maven:1.15.8:mutationCoverage",
    "-DtargetClasses=$TargetClasses",
    "-DtargetTests=$TargetTests",
    "-DexportLineCoverage=true",
    "-Dmutators=NULL_RETURNS",
    "-DoutputFormats=XML",
    "-DtimeoutConstant=10000",
    "-Dfeatures=+coverdictspike",
    "-Dcoverdictspike.classDirs=$classDirs"
  )
  if ($ExcludedTestClasses) {
    $mvnArgs += "-DexcludedTestClasses=$ExcludedTestClasses"
  }

  Write-Host "== Running PIT spike =="
  Push-Location $RepoRoot
  & mvn @mvnArgs
  $exitCode = $LASTEXITCODE
  Pop-Location

  $reportDir = Join-Path $RepoRoot "$ModulePath\target\pit-reports"
  $jsonOut = Join-Path $reportDir "coverdict-line-tests.json"
  if (Test-Path $jsonOut) {
    Write-Host "== Line-level map produced: $jsonOut =="
  } else {
    Write-Warning "No coverdict-line-tests.json produced - check exit code $exitCode above"
  }
}
finally {
  Write-Host "== Reverting throwaway pom.xml =="
  Push-Location $RepoRoot
  & git checkout -- "$ModulePath/pom.xml"
  Pop-Location
}
