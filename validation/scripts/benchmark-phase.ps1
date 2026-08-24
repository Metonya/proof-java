<#
.SYNOPSIS
  Cold + 5-warm benchmark of a coverdict analyze invocation (M1c criterion 6).
  Analyzer-only median/p95 wall time and peak working-set memory.

.PARAMETER Name
  Phase name (e.g. gson), used for the output file name.
.PARAMETER JarPath
  Path to coverdict.jar.
.PARAMETER JarArgs
  Array of arguments to pass to `analyze` (everything after "analyze").
.PARAMETER OutDir
  Directory to write <name>/benchmark.json into.
#>
param(
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$JarPath,
    [Parameter(Mandatory)][string[]]$JarArgs,
    [Parameter(Mandatory)][string]$OutDir
)

function Quote-Arg([string]$a) {
    if ($a -match '[\s"]') {
        return '"' + ($a -replace '"', '\"') + '"'
    }
    return $a
}

function Invoke-TimedRun {
    param([string]$JarPath, [string[]]$JarArgs)

    $allArgs = @("-jar", $JarPath, "analyze") + $JarArgs
    $argString = ($allArgs | ForEach-Object { Quote-Arg $_ }) -join " "

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "java"
    $psi.Arguments = $argString
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    $peakBytes = 0
    $proc = [System.Diagnostics.Process]::Start($psi)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while (-not $proc.HasExited) {
        try {
            $proc.Refresh()
            if ($proc.WorkingSet64 -gt $peakBytes) { $peakBytes = $proc.WorkingSet64 }
        } catch {}
        Start-Sleep -Milliseconds 25
    }
    $sw.Stop()
    $proc.WaitForExit()
    return @{ Millis = $sw.Elapsed.TotalMilliseconds; PeakBytes = $peakBytes; ExitCode = $proc.ExitCode }
}

Write-Host "Cold run..."
$cold = Invoke-TimedRun -JarPath $JarPath -JarArgs $JarArgs

$warm = @()
for ($i = 0; $i -lt 5; $i++) {
    Write-Host "Warm run $($i + 1)/5..."
    $warm += Invoke-TimedRun -JarPath $JarPath -JarArgs $JarArgs
}

$warmMillis = $warm | ForEach-Object { $_.Millis } | Sort-Object
$median = $warmMillis[2]
$p95Index = [Math]::Min(4, [Math]::Ceiling(0.95 * $warmMillis.Count) - 1)
$p95 = $warmMillis[$p95Index]
$peakAll = [Math]::Max($cold.PeakBytes, (($warm | ForEach-Object { $_.PeakBytes } | Measure-Object -Maximum).Maximum))

$result = [ordered]@{
    phase = $Name
    machine = "Windows 11 Pro 26200, x86-64"
    jdk = ((& java -version 2>&1) -join " | ")
    coldMillis = [Math]::Round($cold.Millis, 1)
    warmMillisAll = $warmMillis | ForEach-Object { [Math]::Round($_, 1) }
    warmMedianMillis = [Math]::Round($median, 1)
    warmP95Millis = [Math]::Round($p95, 1)
    peakWorkingSetMb = [Math]::Round($peakAll / 1MB, 1)
    jarArgs = $JarArgs
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$result | ConvertTo-Json -Depth 5 | Out-File -Encoding utf8 (Join-Path $OutDir "benchmark.json")
Write-Host "Wrote $(Join-Path $OutDir 'benchmark.json')"
$result | Format-List
