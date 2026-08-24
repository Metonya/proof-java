<#
.SYNOPSIS
  Clone (if needed) + checkout a pinned corpus repo, bind JaCoCo via CLI
  goals (no pom.xml edits needed in the common case), and run coverdict
  analyze in both --base <pin>~50 and --no-vcs modes. Writes results under
  validation/runs/<Name>/ (M0-VALIDATION-MANIFEST.md Commands section,
  codified for reuse across M1c-2 phases 2-4).

.PARAMETER Name
  Phase name (e.g. gson, assertj, junit-framework, dropwizard).
.PARAMETER RepoUrl
  Git URL to clone.
.PARAMETER Pin
  Pinned commit SHA (docs/M0-VALIDATION-MANIFEST.md).
.PARAMETER CorpusRoot
  Sibling directory holding all corpus clones (outside the coverdict repo).
.PARAMETER ModuleIds / ModuleRoots
  Parallel arrays: coverdict --module <id>=<root> values, one entry per
  bound module (root relative to the repo checkout). A single-module phase
  passes one-element arrays. Multi-module phases (dropwizard, D-37) pass
  several - every module is bound in ONE coverdict invocation, proving
  ModuleBinder's real multi-module path (previously only unit-tested via a
  degenerate same-root case).
.PARAMETER SourceRootRelative / TestRootRelative
  Relative to each module's root; default src/main/java, src/test/java.
  Applied uniformly to every module in ModuleIds/ModuleRoots - all corpus
  phases so far use the standard Maven layout for every bound module.
.PARAMETER JacocoVersion
  Must match pom.xml's jacoco.plugin.version for consistency (D-29-adjacent)
  - the report format is the contract, not the tool version, but a silent,
  undocumented deviation is still not acceptable (M1c-2 assertj precedent:
  an earlier session used 0.8.15 with no rationale recorded anywhere).
.PARAMETER SurefireExcludes
  Test class names to exclude from the -Dtest filter (comma-joined with "!").
  Needed when the corpus repo has ProGuard/obfuscation or jar-dependent
  integration tests that only work under `mvn clean verify`, not under
  JaCoCo-instrumented `test` (gson precedent: EnumWithObfuscatedTest,
  OSGiManifestIT - see validation/runs/gson/run-log.md).
.PARAMETER PomPatches
  Zero or more (search, replace) pairs applied, with sed semantics, to every
  bound module's own pom.xml (each entry in ModuleRoots) - e.g. to flip a
  repo's own <jacoco.skip> off (assertj precedent). Pass as an array of
  2-element arrays: @(@($search1,$replace1), @($search2,$replace2)). Local
  to the throwaway corpus clone only - never touches the coverdict repo,
  and the clone is git-reset before every run (below) so patches never
  stack across repeated invocations.
.PARAMETER LanguageLevel
  --language-level for the oracle critic (match the corpus's testRelease).
.PARAMETER CoverdictJar
  Path to coverdict.jar.
.PARAMETER SkipBuild
  Skip the clone/checkout/build step entirely and just re-run coverdict
  against already-produced jacoco.xml files (e.g. after a coverdict-only
  code change - no need to rebuild a 15-minute corpus test suite just to
  re-run the analyzer).
.PARAMETER BuildTool
  "Maven" (default) or "Gradle". Selects the build/report-path branch below;
  all Maven-only parameters (JacocoVersion, SurefireExcludes, PomPatches)
  are ignored in Gradle mode. Gradle mode only supports a single module
  (GradleModule) - junit-framework's Isolated-Projects convention plugins
  make a real multi-module Gradle binding a separate design task (D-36);
  not needed by any phase so far. Per D-23, the host build tool is
  otherwise irrelevant to coverdict itself - only this harness script cares.
.PARAMETER GradleModule
  Gradle project path without a leading colon (e.g. "junit-vintage-engine").
  Mandatory when -BuildTool Gradle. The build runs only
  ":$GradleModule:test :$GradleModule:jacocoTestReport" (plus whatever
  sibling projects Gradle pulls in transitively), not the whole reactor.
.PARAMETER GradleInitScript
  Content of a Gradle init script applied via --init-script from a temp
  file - never written into the clone itself (cleaner than PomPatches,
  which do touch the clone). Defaults to a script that turns on JacocoReport
  XML output (off by default in Gradle's jacoco plugin; junit-framework's
  own convention plugins don't turn it on either - only their whole-repo
  aggregation report does, which this harness deliberately avoids, see
  D-36). Applies to -BuildTool Gradle only.
#>
param(
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$RepoUrl,
    [Parameter(Mandatory)][string]$Pin,
    [string]$CorpusRoot = "C:\Users\Mert\Desktop\coverdict-corpus",
    [Parameter(Mandatory)][string[]]$ModuleIds,
    [Parameter(Mandatory)][string[]]$ModuleRoots,
    [string]$SourceRootRelative = "src/main/java",
    [string]$TestRootRelative = "src/test/java",
    [string]$JacocoVersion = "0.8.13",
    [string[]]$SurefireExcludes = @(),
    [object[]]$PomPatches = @(),
    [int]$LanguageLevel = 17,
    [Parameter(Mandatory)][string]$CoverdictJar,
    [string]$OutDir,
    [switch]$SkipBuild,
    [ValidateSet("Maven", "Gradle")][string]$BuildTool = "Maven",
    [string]$GradleModule,
    [string]$GradleInitScript = @"
// allprojects{} reaches across project boundaries, which junit-framework
// rejects (org.gradle.isolated-projects=true in its gradle.properties) with
// "cannot access 'Project.plugins' functionality on subprojects via
// 'allprojects'" - both in the outer build and in its gradle/plugins
// included build (see D-36). gradle.beforeProject configures each project
// from within its own configuration phase instead, which Isolated Projects
// allows.
gradle.beforeProject {
    plugins.withId("jacoco") {
        tasks.withType<JacocoReport>().configureEach {
            reports { xml.required.set(true) }
        }
    }
}
"@
)

if ($ModuleIds.Count -eq 0) { throw "-ModuleIds must have at least one entry" }
if ($ModuleIds.Count -ne $ModuleRoots.Count) {
    throw "-ModuleIds ($($ModuleIds.Count)) and -ModuleRoots ($($ModuleRoots.Count)) must have the same count"
}
if ($BuildTool -eq "Gradle") {
    if (-not $GradleModule) { throw "-GradleModule is required when -BuildTool Gradle" }
    if ($ModuleIds.Count -gt 1) { throw "multi-module binding is Maven-only today (D-37) - Gradle mode supports exactly one -GradleModule" }
}

if (-not $OutDir) { $OutDir = "C:\Users\Mert\Desktop\coverdict\validation\runs\$Name" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$repoDir = Join-Path $CorpusRoot $Name

if (-not (Test-Path $repoDir)) {
    & git clone --quiet $RepoUrl $repoDir
    if ($LASTEXITCODE -ne 0) { throw "git clone failed" }
}
Push-Location $repoDir
try {
    if (-not $SkipBuild) {
        # Discard any patches left over from a previous run of this script
        # before applying this run's own - patches must never stack.
        & git reset --hard --quiet HEAD
        & git clean -fdq
        & git checkout --quiet $Pin
        if ($LASTEXITCODE -ne 0) { throw "git checkout $Pin failed" }

        if ($BuildTool -eq "Maven") {
            foreach ($root in $ModuleRoots) {
                $modulePomPath = Join-Path $root "pom.xml"
                foreach ($patch in $PomPatches) {
                    if ($patch.Count -ne 2) { throw "each PomPatches entry must be a (search, replace) pair" }
                    $content = Get-Content $modulePomPath -Raw
                    if ($content -notmatch [regex]::Escape($patch[0])) {
                        throw "pom patch search text not found in ${modulePomPath}: $($patch[0])"
                    }
                    ($content -replace [regex]::Escape($patch[0]), $patch[1]) |
                        Set-Content -Path $modulePomPath -NoNewline
                }
            }

            $testFilter = if ($SurefireExcludes.Count -gt 0) {
                "-Dtest=" + (($SurefireExcludes | ForEach-Object { "!$_" }) -join ",")
            } else { $null }

            $modulePl = $ModuleRoots -join ","
            $mvnArgs = @("-q", "-B", "-pl", $modulePl, "-am")
            if ($testFilter) { $mvnArgs += $testFilter; $mvnArgs += "-DfailIfNoTests=false" }
            $mvnArgs += @("clean", "org.jacoco:jacoco-maven-plugin:${JacocoVersion}:prepare-agent",
                          "test", "org.jacoco:jacoco-maven-plugin:${JacocoVersion}:report")
            & mvn.cmd @mvnArgs
            if ($LASTEXITCODE -ne 0) { throw "mvn build failed (exit $LASTEXITCODE)" }
        } else {
            # Gradle branch (D-36): no pom-style patch, no surefire excludes,
            # no jacoco.plugin.version pin - the repo's own convention
            # plugins own all of that. --init-script is applied from a temp
            # file so the clone itself is never edited. --no-daemon so this
            # one-shot corpus run never leaves a background JVM resident
            # (D-35 lesson: don't let a process balloon unattended).
            $initScriptPath = Join-Path ([System.IO.Path]::GetTempPath()) "coverdict-gradle-init-$Name.gradle.kts"
            $GradleInitScript | Out-File -Encoding utf8 -FilePath $initScriptPath -NoNewline
            $gradleTarget = ":${GradleModule}:test"
            $gradleReportTarget = ":${GradleModule}:jacocoTestReport"
            & .\gradlew.bat --no-daemon --init-script $initScriptPath -q $gradleTarget $gradleReportTarget
            if ($LASTEXITCODE -ne 0) { throw "gradlew build failed (exit $LASTEXITCODE)" }
        }
    }

    # Resolve one jacoco.xml per bound module and build the repeated
    # --module/--source-roots/--test-roots/--report flags coverdict expects
    # (D-37: previously exactly one of each; now N, one set per module).
    $moduleFlags = @()
    $srcFlags = @()
    $testFlags = @()
    $reportFlags = @()
    $moduleReportPaths = [ordered]@{}
    for ($i = 0; $i -lt $ModuleIds.Count; $i++) {
        $id = $ModuleIds[$i]
        $root = $ModuleRoots[$i]
        $jacocoXml = if ($BuildTool -eq "Maven") {
            Join-Path $root "target\site\jacoco\jacoco.xml"
        } else {
            Join-Path $root "build\reports\jacoco\test\jacocoTestReport.xml"
        }
        if (-not (Test-Path $jacocoXml)) { throw "jacoco.xml not produced at $jacocoXml (module $id)" }

        $reportArg = "$id=" + ($jacocoXml -replace [regex]::Escape($repoDir + "\"), "" -replace "\\", "/")
        $moduleArg = "$id=$root" -replace "\\", "/"
        $srcArg = "$id=$root/$SourceRootRelative" -replace "\\", "/"
        $testArg = "$id=$root/$TestRootRelative" -replace "\\", "/"

        $moduleFlags += @("--module", $moduleArg)
        $srcFlags += @("--source-roots", $srcArg)
        $testFlags += @("--test-roots", $testArg)
        $reportFlags += @("--report", $reportArg)
        $moduleReportPaths[$id] = $jacocoXml
    }
    $analyzeCommonArgs = $moduleFlags + $srcFlags + $testFlags + $reportFlags + @("--language-level", $LanguageLevel)

    & java -jar $CoverdictJar analyze --repo . --base "${Pin}~50" @analyzeCommonArgs `
        --out (Join-Path $OutDir "verdict-base.json")
    Write-Host "base-ref run exit: $LASTEXITCODE"

    & java -jar $CoverdictJar analyze --repo . --no-vcs @analyzeCommonArgs `
        --out (Join-Path $OutDir "verdict-no-vcs.json")
    Write-Host "no-vcs run exit: $LASTEXITCODE"

    Remove-Item -Force (Join-Path $repoDir "coverdict-verdict.json") -ErrorAction SilentlyContinue
} finally {
    Pop-Location
}

$patchStatus = if ($SkipBuild) {
    "required on a fresh (non -SkipBuild) build - NOT (re)applied this run, this run reused the clone's existing state as-is"
} elseif ($BuildTool -eq "Maven") {
    "applied to the throwaway clone only - never committed to the coverdict repo"
} else {
    "n/a - Gradle branch uses --init-script instead (below), never edits the clone"
}

$moduleTable = ($ModuleIds | ForEach-Object -Begin { $i = 0 } -Process {
    $line = "  - $_ = $($ModuleRoots[$i])"
    $i++
    $line
}) -join "`n"

$buildSection = if ($BuildTool -eq "Maven") {
    $patchLines = if ($PomPatches.Count -eq 0) {
        "(none)"
    } else {
        ($PomPatches | ForEach-Object { "  - `"$($_[0])`" -> `"$($_[1])`"" }) -join "`n"
    }
    @"
Build tool: Maven
JaCoCo: $JacocoVersion (bound via CLI goals)
Surefire excludes: $($SurefireExcludes -join ', ')
pom.xml patches, applied to every module listed above ($patchStatus):
$patchLines

Commands (reactor-relative, run from ${repoDir}):
  mvn -B -pl $($ModuleRoots -join ',') -am clean org.jacoco:jacoco-maven-plugin:${JacocoVersion}:prepare-agent test org.jacoco:jacoco-maven-plugin:${JacocoVersion}:report
"@
} else {
    @"
Build tool: Gradle ($GradleModule)
JaCoCo: repo-pinned version (see gradle/libs.versions.toml), not overridden by this harness
Surefire excludes: n/a (Maven-only)
pom.xml patches: n/a (Maven-only)
Gradle init script ($patchStatus):
$GradleInitScript

Commands (run from ${repoDir}):
  gradlew.bat --no-daemon --init-script <temp-file-above> :${GradleModule}:test :${GradleModule}:jacocoTestReport
"@
}

$fullCommandArgs = ($analyzeCommonArgs | ForEach-Object { if ($_ -match '[\s"]') { "`"$_`"" } else { $_ } }) -join " "

@"
# $Name - corpus phase run log

Repo: $RepoUrl @ $Pin
Modules:
$moduleTable
$buildSection
Language level: $LanguageLevel

  java -jar coverdict.jar analyze --repo . --base ${Pin}~50 $fullCommandArgs --out verdict-base.json
  java -jar coverdict.jar analyze --repo . --no-vcs $fullCommandArgs --out verdict-no-vcs.json
"@ | Out-File -Encoding utf8 (Join-Path $OutDir "run-log.md")

Write-Host "Wrote $(Join-Path $OutDir 'run-log.md')"
