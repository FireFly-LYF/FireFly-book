# FireFly-book one-click stop
# Usage:
#   .\stop-all.ps1
#   .\stop-all.ps1 -StopDocker

param(
    [switch]$StopDocker
)

$ErrorActionPreference = "Continue"

function Stop-Port([int]$Port) {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "  :$Port free"
        return
    }
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $pids) {
        try {
            $p = Get-Process -Id $procId -ErrorAction Stop
            Write-Host ("  kill PID=" + $procId + " (" + $p.ProcessName + ") on :" + $Port) -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host ("  cannot kill PID=" + $procId + " (:" + $Port + ")") -ForegroundColor DarkYellow
        }
    }
}

Write-Host "Stopping FireFly ports..." -ForegroundColor Cyan
foreach ($port in @(5173, 8080, 9001, 9002, 9003, 9004, 9005, 9006, 9007)) {
    Stop-Port -Port $port
}

if ($StopDocker) {
    Write-Host ""
    Write-Host "Stopping Docker containers..." -ForegroundColor Cyan
    foreach ($c in @("es", "rabbitmq", "redis", "mysql")) {
        $running = @(docker ps --format "{{.Names}}" 2>$null)
        if ($running -contains $c) {
            docker stop $c | Out-Null
            Write-Host "  stopped $c"
        } else {
            Write-Host "  $c not running"
        }
    }
}

Write-Host ""
Write-Host "Done. Close leftover Java/Go windows manually if needed." -ForegroundColor Green
