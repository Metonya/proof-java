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
  Must match pom.xml's jacoco.plugin.version for consistency (D-29-adjacent).
.PARAMETER SurefireExcludes
  Test class names to exclude from the -Dtest filter (comma-joined with "!").
  Needed when the corpus repo has ProGuard/obfuscation or jar-dependent
  integration tests that only work under `mvn clean verify`, not under
  JaCoCo-instrumented `test` (gson precedent: EnumWithObfuscatedTest,
  OSGiManifestIT - see validation/runs/gson/run-log.md).
.PARAMETER ArgLinePomPatch
  If set, a (search, replace) pair applied to the module's pom.xml with sed
  semantics, to let JaCoCo's ${argLine} property reach a hardcoded
  <argLine> element (gson precedent). Local to the throwaway corpus clone
  only - never touches the coverdict repo.
.PARAMETER LanguageLevel
  --language-level for the oracle critic (match the corpus's testRelease).
.PARAMETER CoverdictJar
  Path to coverdict.jar.
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
    [string[]]$ArgLinePomPatch = @(),
    [int]$LanguageLevel = 17,
    [Parameter(Mandatory)][string]$CoverdictJar,
    [string]$OutDir
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
    & git checkout --quiet $Pin
    if ($LASTEXITCODE -ne 0) { throw "git checkout $Pin failed" }

    $modulePomPath = Join-Path $ModuleRoot "pom.xml"
    if ($ArgLinePomPatch.Count -eq 2) {
        (Get-Content $modulePomPath -Raw) -replace [regex]::Escape($ArgLinePomPatch[0]), $ArgLinePomPatch[1] |
            Set-Content -Path $modulePomPath -NoNewline
    }

    $testFilter = if ($SurefireExcludes.Count -gt 0) {
        "-Dtest=" + (($SurefireExcludes | ForEach-Object { "!$_" }) -join ",")
    } else { $null }

    $mvnArgs = @("-q", "-B", "-pl", $ModuleRoot, "-am")
    if ($testFilter) { $mvnArgs += $testFilter; $mvnArgs += "-DfailIfNoTests=false" }
    $mvnArgs += @("clean", "org.jacoco:jacoco-maven-plugin:${JacocoVersion}:prepare-agent",
                  "test", "org.jacoco:jacoco-maven-plugin:${JacocoVersion}:report")
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "mvn build failed (exit $LASTEXITCODE)" }

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

@"
# $Name - corpus phase run log

Repo: $RepoUrl @ $Pin
Module: $ModuleId at $ModuleRoot
JaCoCo: $JacocoVersion (bound via CLI goals, no pom edit unless ArgLinePomPatch set)
Surefire excludes: $($SurefireExcludes -join ', ')
ArgLine pom patch applied: $($ArgLinePomPatch.Count -eq 2)
Language level: $LanguageLevel

Commands (module-relative, run from $repoDir):
  mvn -B -pl $ModuleRoot -am clean org.jacoco:jacoco-maven-plugin:${JacocoVersion}:prepare-agent test org.jacoco:jacoco-maven-plugin:${JacocoVersion}:report
  java -jar coverdict.jar analyze --repo . --base ${Pin}~50 --module $moduleArg --source-roots $srcArg --test-roots $testArg --report $reportArg --language-level $LanguageLevel --out verdict-base.json
  java -jar coverdict.jar analyze --repo . --no-vcs (same module/source/test/report args) --out verdict-no-vcs.json
"@ | Out-File -Encoding utf8 (Join-Path $OutDir "run-log.md")

Write-Host "Wrote $(Join-Path $OutDir 'run-log.md')"
