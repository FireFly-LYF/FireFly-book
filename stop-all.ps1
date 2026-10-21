# Stop processes started by start-all.ps1
# Usage:
#   .\stop-all.ps1
#   .\stop-all.ps1 -StopDocker

param(
    [switch]$StopDocker
)

$ErrorActionPreference = "Continue"
$RepoRoot = $PSScriptRoot
$EnvFile = Join-Path $RepoRoot ".env"
$StateDir = Join-Path $RepoRoot ".firefly-dev"
$PidFile = Join-Path $StateDir "pids.json"
$ComposeFile = Join-Path $RepoRoot "deploy\docker-compose.yml"

$DevPorts = @(9001, 9002, 9003, 9004, 9005, 9006, 9007, 9008, 8080, 5173)

function Stop-PidsParallel([int[]]$Pids, [string]$ReasonPrefix = "") {
    $unique = @($Pids | Where-Object { $_ -gt 4 } | Select-Object -Unique)
    if ($unique.Count -eq 0) { return }

    $alive = @()
    foreach ($procId in $unique) {
        $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($p) {
            Write-Host ("  stop pid={0} ({1}){2}" -f $procId, $p.ProcessName, $ReasonPrefix)
            $alive += $procId
        }
    }
    if ($alive.Count -eq 0) { return }

    # Fire taskkill in parallel; /T kills the process tree.
    $jobs = foreach ($procId in $alive) {
        Start-Process -FilePath "taskkill.exe" `
            -ArgumentList @("/PID", "$procId", "/T", "/F") `
            -WindowStyle Hidden `
            -PassThru
    }
    if ($jobs) {
        $null = Wait-Process -InputObject $jobs -Timeout 8 -ErrorAction SilentlyContinue
        foreach ($j in $jobs) {
            if (-not $j.HasExited) {
                try { Stop-Process -Id $j.Id -Force -ErrorAction SilentlyContinue } catch {}
            }
        }
    }
}

function Get-ListenerPidsOnPorts([int[]]$Ports) {
    $portSet = @{}
    foreach ($p in $Ports) { $portSet[$p] = $true }

    # netstat is much faster than repeated Get-NetTCPConnection calls.
    $pids = [System.Collections.Generic.HashSet[int]]::new()
    $lines = & netstat.exe -ano -p tcp 2>$null
    foreach ($line in $lines) {
        if ($line -notmatch 'LISTENING') { continue }
        $parts = ($line -split '\s+') | Where-Object { $_ -ne '' }
        if ($parts.Count -lt 5) { continue }
        # Proto LocalAddress ForeignAddress State PID
        $local = $parts[1]
        $procId = 0
        if (-not [int]::TryParse($parts[-1], [ref]$procId)) { continue }
        $colon = $local.LastIndexOf(':')
        if ($colon -lt 0) { continue }
        $portText = $local.Substring($colon + 1)
        $port = 0
        if (-not [int]::TryParse($portText, [ref]$port)) { continue }
        if ($portSet.ContainsKey($port)) {
            [void]$pids.Add($procId)
        }
    }
    return @($pids)
}

function Stop-TrackedProcesses {
    if (-not (Test-Path -LiteralPath $PidFile)) { return }
    try {
        $state = Get-Content -LiteralPath $PidFile -Raw | ConvertFrom-Json
    } catch {
        return
    }
    $ids = @()
    if ($state.java) {
        foreach ($j in $state.java) {
            if ($j.pid) { $ids += [int]$j.pid }
        }
    }
    if ($state.gateway) { $ids += [int]$state.gateway }
    if ($state.frontend) { $ids += [int]$state.frontend }
    Stop-PidsParallel -Pids $ids
}

function Stop-DevPortListeners {
    $pids = Get-ListenerPidsOnPorts -Ports $DevPorts
    Stop-PidsParallel -Pids $pids -ReasonPrefix " (port listener)"
}

Write-Host "==> Stop local dev processes" -ForegroundColor Cyan
Stop-TrackedProcesses

Write-Host "==> Cleanup listeners by port" -ForegroundColor Cyan
Stop-DevPortListeners

# Quick second pass for late-binding listeners (no long sleep)
Start-Sleep -Milliseconds 300
Stop-DevPortListeners

if (Test-Path -LiteralPath $PidFile) {
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
}

if ($StopDocker) {
    Write-Host "==> Stop Docker infra" -ForegroundColor Cyan
    if (Test-Path -LiteralPath $EnvFile) {
        & docker compose -f $ComposeFile --env-file $EnvFile stop mysql redis rabbitmq elasticsearch
    } else {
        & docker compose -f $ComposeFile stop mysql redis rabbitmq elasticsearch
    }
}

Write-Host ""
Write-Host "Stopped local Java / Gateway / frontend." -ForegroundColor Green
if ($StopDocker) {
    Write-Host "Infra containers stopped (volumes kept)." -ForegroundColor Green
} else {
    Write-Host "Docker infra still running (add -StopDocker if needed)." -ForegroundColor Yellow
}
