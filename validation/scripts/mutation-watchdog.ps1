<#
.SYNOPSIS
  D-59 safety harness: runs a command (default: `mvn -Pmutation-it -pl
  coverdict-cli test`) while polling the live descendant-process count and
  total working-set memory once a second, force-killing the entire process
  tree the instant either threshold is crossed.

.DESCRIPTION
  Reproducing D-59 live means deliberately re-running the exact scenario
  that brought this machine to three near-OOM incidents in a prior session
  (60+ processes within ~2 minutes, free memory under 2GB twice).
  `SubprocessWorkspace.destroyProcessTree` already reaches the whole
  descendant tree via `taskkill /F /T` when MutationRunner's own budget
  expires - but that is an after-the-fact cleanup, triggered only once the
  budget elapses. This script is meant to be the missing *during-the-run*
  guard, independent of whether coverdict's own budget logic ever fires.

  CORRECTION (found live, 2026-08-25): an earlier version of this script
  filtered `Get-CimInstance Win32_Process` to `Name = 'java.exe'` before
  walking parent links. That is wrong and was silently blind to a real
  incident: a live run climbed to 44 processes and 18+GB (Task Manager,
  observed directly by the user) while this script's own polling loop
  reported "count=1" the entire time and never crossed either threshold.
  The most likely explanation is a `javaw.exe`-named or otherwise
  differently-named process breaking the java.exe-only parent chain at some
  hop - never fully root-caused, and not worth trusting a name-based filter
  ever again for a safety tool. This version tracks the **entire** process
  tree descended from the watchdog's own child process, with no name
  filter at all - correctness here matters far more than the extra noise
  from counting a few non-JVM helper processes.

.PARAMETER Command
  The command to run under the watchdog. Default: `mvn -Pmutation-it -pl
  coverdict-cli test`, run from RepoRoot.
.PARAMETER RepoRoot
  Working directory for Command. Default: this script's repo root.
.PARAMETER MaxProcesses
  Kill everything once the live descendant process count (any name) reaches
  this. Default 15 - D-59's incident crossed 60; this stops over an order
  of magnitude earlier.
.PARAMETER MaxWorkingSetMB
  Kill everything once the summed working set of all descendant processes
  (any name) reaches this many MB. Default 3072 (3GB) - a live incident
  this script's earlier, buggy version failed to catch reached 18+GB; this
  is a conservative absolute ceiling on the watched subtree alone, not
  total system memory.
.PARAMETER PollIntervalSeconds
  How often to sample. Default 1 (matches D-59's "climbed past 60 within
  roughly two minutes" granularity - anything coarser risks missing a fast
  spike).
.PARAMETER LogPath
  Where to write the per-sample process/memory log (CSV) and the final
  verdict. Default: validation/runs/mutation-watchdog/<timestamp>/.

.EXAMPLE
  ./mutation-watchdog.ps1

.EXAMPLE
  ./mutation-watchdog.ps1 -MaxProcesses 10 -MaxWorkingSetMB 2048
#>
[CmdletBinding()]
param(
    [string]$Command = 'mvn -Pmutation-it -pl coverdict-cli test',
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [int]$MaxProcesses = 15,
    [int]$MaxWorkingSetMB = 3072,
    [double]$PollIntervalSeconds = 1,
    [string]$LogPath = ''
)

$ErrorActionPreference = 'Stop'

if (-not $LogPath) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $LogPath = Join-Path $RepoRoot "validation\runs\mutation-watchdog\$stamp"
}
New-Item -ItemType Directory -Force -Path $LogPath | Out-Null
$csvPath = Join-Path $LogPath 'samples.csv'
$verdictPath = Join-Path $LogPath 'verdict.txt'

'timestamp,elapsedSeconds,processCount,totalWorkingSetMB' | Out-File -FilePath $csvPath -Encoding utf8

function Get-DescendantProcesses {
    param([int]$RootPid)
    # Walk Win32_Process parent links from the watchdog's own child, over
    # EVERY process on the machine regardless of name - only processes
    # actually descended from this run are ever counted or touched, but
    # "descended from this run" is determined purely by the PID graph, never
    # by matching an executable name (see the class-level CORRECTION note:
    # a name filter here previously went blind to a real 44-process/18GB
    # incident).
    $all = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue
    if (-not $all) {
        return @()
    }
    $byParent = @{}
    foreach ($p in $all) {
        $key = [string]$p.ParentProcessId
        if (-not $byParent.ContainsKey($key)) {
            $byParent[$key] = New-Object System.Collections.Generic.List[object]
        }
        $byParent[$key].Add($p)
    }
    $frontier = New-Object System.Collections.Generic.Queue[int]
    $frontier.Enqueue($RootPid)
    $found = New-Object System.Collections.Generic.List[object]
    $visited = New-Object System.Collections.Generic.HashSet[int]
    while ($frontier.Count -gt 0) {
        $currentPid = $frontier.Dequeue()
        if (-not $visited.Add($currentPid)) { continue }
        $key = [string]$currentPid
        if ($byParent.ContainsKey($key)) {
            foreach ($child in $byParent[$key]) {
                $found.Add($child)
                $frontier.Enqueue([int]$child.ProcessId)
            }
        }
    }
    return $found
}

function Kill-Tree {
    param([int]$RootPid)
    try {
        & "$env:SystemRoot\System32\taskkill.exe" /F /T /PID $RootPid 2>$null | Out-Null
    } catch {
        # best-effort - the polling loop below independently kills every
        # descendant process it can still see regardless of this call's outcome
    }
}

Write-Host "Starting under watchdog: $Command"
Write-Host "Thresholds: $MaxProcesses processes, ${MaxWorkingSetMB}MB total working set, ${PollIntervalSeconds}s poll"
Write-Host "Log: $LogPath"

Push-Location $RepoRoot
try {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = "/c $Command"
    $psi.UseShellExecute = $false
    $proc = [System.Diagnostics.Process]::Start($psi)
    $rootPid = $proc.Id
    $startTime = Get-Date

    $verdict = 'UNKNOWN'
    $reason = ''
    $peakProcesses = 0
    $peakMemoryMB = 0

    while (-not $proc.HasExited) {
        Start-Sleep -Seconds $PollIntervalSeconds
        $descendants = @(Get-DescendantProcesses -RootPid $rootPid)
        $count = $descendants.Count
        $totalMB = 0
        foreach ($d in $descendants) {
            $totalMB += [math]::Round($d.WorkingSetSize / 1MB, 1)
        }
        $elapsed = [math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)
        "$([DateTime]::Now.ToString('o')),$elapsed,$count,$totalMB" | Out-File -FilePath $csvPath -Append -Encoding utf8
        if ($count -gt $peakProcesses) { $peakProcesses = $count }
        if ($totalMB -gt $peakMemoryMB) { $peakMemoryMB = $totalMB }
        Write-Host ("[{0,6:N1}s] descendant procs={1,3}  totalWorkingSet={2,8:N1} MB  (peak {3} procs / {4:N1} MB)" -f $elapsed, $count, $totalMB, $peakProcesses, $peakMemoryMB)

        if ($count -ge $MaxProcesses) {
            $verdict = 'KILLED_PROCESS_THRESHOLD'
            $reason = "process count $count reached MaxProcesses=$MaxProcesses at ${elapsed}s"
            Write-Host "THRESHOLD CROSSED: $reason - killing tree"
            Kill-Tree -RootPid $rootPid
            foreach ($d in $descendants) {
                Kill-Tree -RootPid $d.ProcessId
            }
            break
        }
        if ($totalMB -ge $MaxWorkingSetMB) {
            $verdict = 'KILLED_MEMORY_THRESHOLD'
            $reason = "working set ${totalMB}MB reached MaxWorkingSetMB=$MaxWorkingSetMB at ${elapsed}s"
            Write-Host "THRESHOLD CROSSED: $reason - killing tree"
            Kill-Tree -RootPid $rootPid
            foreach ($d in $descendants) {
                Kill-Tree -RootPid $d.ProcessId
            }
            break
        }
    }

    if ($verdict -eq 'UNKNOWN') {
        $proc.WaitForExit()
        if ($proc.ExitCode -eq 0) {
            $verdict = 'COMPLETED_CLEAN'
            $reason = "command exited 0 without crossing either threshold"
        } else {
            $verdict = 'COMPLETED_NONZERO'
            $reason = "command exited $($proc.ExitCode) without crossing either threshold"
        }
    }

    $summary = @"
verdict: $verdict
reason: $reason
peakProcesses: $peakProcesses
peakWorkingSetMB: $peakMemoryMB
thresholds: MaxProcesses=$MaxProcesses MaxWorkingSetMB=$MaxWorkingSetMB
samples: $csvPath
"@
    $summary | Out-File -FilePath $verdictPath -Encoding utf8
    Write-Host "`n$summary"
} finally {
    Pop-Location
}
