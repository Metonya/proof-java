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
.PARAMETER ModuleId / ModuleRoot
  coverdict --module <id>=<root> value (root relative to the repo checkout).
.PARAMETER SourceRootRelative / TestRootRelative
  Relative to ModuleRoot; default src/main/java, src/test/java.
.PARAMETER JacocoVersion
  Must match pom.xml's jacoco.plugin.version (0.8.13) for consistency
  (D-29-adjacent) - the report format is the contract, not the tool
  version, but a silent, undocumented deviation is still not acceptable
  (M1c-2 assertj precedent: an earlier session used 0.8.15 with no
  rationale recorded anywhere).
.PARAMETER SurefireExcludes
  Test class names to exclude from the -Dtest filter (comma-joined with "!").
  Needed when the corpus repo has ProGuard/obfuscation or jar-dependent
  integration tests that only work under `mvn clean verify`, not under
  JaCoCo-instrumented `test` (gson precedent: EnumWithObfuscatedTest,
  OSGiManifestIT - see validation/runs/gson/run-log.md).
.PARAMETER PomPatches
  Zero or more (search, replace) pairs applied to the module's pom.xml with
  sed semantics, e.g. to let JaCoCo's ${argLine} property reach a hardcoded
  <argLine> element, or to flip a repo's own <jacoco.skip> off (assertj
  precedent - three separate patches were needed on one file). Pass as an
  array of 2-element arrays: @(@($search1,$replace1), @($search2,$replace2)).
  Local to the throwaway corpus clone only - never touches the coverdict
  repo, and the clone is git-reset before every run (below) so patches
  never stack across repeated invocations.
.PARAMETER LanguageLevel
  --language-level for the oracle critic (match the corpus's testRelease).
.PARAMETER CoverdictJar
  Path to coverdict.jar.
.PARAMETER SkipBuild
  Skip the clone/checkout/mvn-build step entirely and just re-run coverdict
  against an already-produced jacoco.xml (e.g. after a coverdict-only code
  change - no need to rebuild a 15-minute corpus test suite just to re-run
  the analyzer).
#>
param(
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$RepoUrl,
    [Parameter(Mandatory)][string]$Pin,
    [string]$CorpusRoot = "C:\Users\Mert\Desktop\coverdict-corpus",
    [Parameter(Mandatory)][string]$ModuleId,
    [Parameter(Mandatory)][string]$ModuleRoot,
    [string]$SourceRootRelative = "src/main/java",
    [string]$TestRootRelative = "src/test/java",
    [string]$JacocoVersion = "0.8.13",
    [string[]]$SurefireExcludes = @(),
    [object[]]$PomPatches = @(),
    [int]$LanguageLevel = 17,
    [Parameter(Mandatory)][string]$CoverdictJar,
    [string]$OutDir,
    [switch]$SkipBuild
)

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

        $modulePomPath = Join-Path $ModuleRoot "pom.xml"
        foreach ($patch in $PomPatches) {
            if ($patch.Count -ne 2) { throw "each PomPatches entry must be a (search, replace) pair" }
            $content = Get-Content $modulePomPath -Raw
            if ($content -notmatch [regex]::Escape($patch[0])) {
                throw "pom patch search text not found in ${modulePomPath}: $($patch[0])"
            }
            ($content -replace [regex]::Escape($patch[0]), $patch[1]) |
                Set-Content -Path $modulePomPath -NoNewline
        }

        $testFilter = if ($SurefireExcludes.Count -gt 0) {
            "-Dtest=" + (($SurefireExcludes | ForEach-Object { "!$_" }) -join ",")
        } else { $null }

        $mvnArgs = @("-q", "-B", "-pl", $ModuleRoot, "-am")
        if ($testFilter) { $mvnArgs += $testFilter; $mvnArgs += "-DfailIfNoTests=false" }
        $mvnArgs += @("clean", "org.jacoco:jacoco-maven-plugin:${JacocoVersion}:prepare-agent",
                      "test", "org.jacoco:jacoco-maven-plugin:${JacocoVersion}:report")
        & mvn.cmd @mvnArgs
        if ($LASTEXITCODE -ne 0) { throw "mvn build failed (exit $LASTEXITCODE)" }
    }

    $jacocoXml = Join-Path $ModuleRoot "target\site\jacoco\jacoco.xml"
    if (-not (Test-Path $jacocoXml)) { throw "jacoco.xml not produced at $jacocoXml" }

    $reportArg = "$ModuleId=" + ($jacocoXml -replace [regex]::Escape($repoDir + "\"), "" -replace "\\", "/")
    $moduleArg = "$ModuleId=$ModuleRoot" -replace "\\", "/"
    $srcArg = "$ModuleId=$ModuleRoot/$SourceRootRelative" -replace "\\", "/"
    $testArg = "$ModuleId=$ModuleRoot/$TestRootRelative" -replace "\\", "/"

    & java -jar $CoverdictJar analyze --repo . --base "${Pin}~50" `
        --module $moduleArg --source-roots $srcArg --test-roots $testArg `
        --report $reportArg --language-level $LanguageLevel `
        --out (Join-Path $OutDir "verdict-base.json")
    Write-Host "base-ref run exit: $LASTEXITCODE"

    & java -jar $CoverdictJar analyze --repo . --no-vcs `
        --module $moduleArg --source-roots $srcArg --test-roots $testArg `
        --report $reportArg --language-level $LanguageLevel `
        --out (Join-Path $OutDir "verdict-no-vcs.json")
    Write-Host "no-vcs run exit: $LASTEXITCODE"

    Remove-Item -Force (Join-Path $repoDir "coverdict-verdict.json") -ErrorAction SilentlyContinue
} finally {
    Pop-Location
}

$patchLines = if ($PomPatches.Count -eq 0) {
    "(none)"
} else {
    ($PomPatches | ForEach-Object { "  - `"$($_[0])`" -> `"$($_[1])`"" }) -join "`n"
}
$patchStatus = if ($SkipBuild) {
    "required on a fresh (non -SkipBuild) build - NOT (re)applied this run, this run reused the clone's existing state as-is"
} else {
    "applied to the throwaway clone only - never committed to the coverdict repo"
}

@"
# $Name - corpus phase run log

Repo: $RepoUrl @ $Pin
Module: $ModuleId at $ModuleRoot
JaCoCo: $JacocoVersion (bound via CLI goals)
Surefire excludes: $($SurefireExcludes -join ', ')
pom.xml patches ($patchStatus):
$patchLines
Language level: $LanguageLevel

Commands (module-relative, run from $repoDir):
  mvn -B -pl $ModuleRoot -am clean org.jacoco:jacoco-maven-plugin:${JacocoVersion}:prepare-agent test org.jacoco:jacoco-maven-plugin:${JacocoVersion}:report
  java -jar coverdict.jar analyze --repo . --base ${Pin}~50 --module $moduleArg --source-roots $srcArg --test-roots $testArg --report $reportArg --language-level $LanguageLevel --out verdict-base.json
  java -jar coverdict.jar analyze --repo . --no-vcs (same module/source/test/report args) --out verdict-no-vcs.json
"@ | Out-File -Encoding utf8 (Join-Path $OutDir "run-log.md")

Write-Host "Wrote $(Join-Path $OutDir 'run-log.md')"
