#Requires -Version 5.1
<#
.SYNOPSIS
  覆盖重建 FireFly Docker 全栈（infra + Java + Gateway），默认保留命名卷数据。

.EXAMPLE
  .\deploy\scripts\redeploy.ps1
  .\deploy\scripts\redeploy.ps1 -SkipStopLocal
  .\deploy\scripts\redeploy.ps1 -WipeVolumes
#>
param(
  [switch]$SkipStopLocal,
  [switch]$WipeVolumes,
  [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComposeFile = Join-Path $RepoRoot 'deploy\docker-compose.yml'
$EnvFile = Join-Path $RepoRoot '.env'
$StopAll = Join-Path $RepoRoot 'stop-all.ps1'
$SchemaSql = Join-Path $RepoRoot 'services\market-service\schema.sql'

Set-Location $RepoRoot

if (-not (Test-Path -LiteralPath $ComposeFile)) {
  throw "missing $ComposeFile"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
  throw 'missing .env — copy .env.example .env and fill secrets first'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw 'docker not found'
}

function Import-DotEnv([string]$Path) {
  foreach ($raw in Get-Content -LiteralPath $Path) {
    $line = $raw.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { continue }
    $idx = $line.IndexOf('=')
    if ($idx -lt 1) { continue }
    $key = $line.Substring(0, $idx).Trim()
    $val = $line.Substring($idx + 1).Trim()
    if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
      $val = $val.Substring(1, $val.Length - 2)
    }
    Set-Item -Path "Env:$key" -Value $val
  }
}

Import-DotEnv $EnvFile
if (-not $env:MYSQL_PASSWORD) {
  throw 'MYSQL_PASSWORD missing in .env'
}

Write-Host ''
Write-Host '==> Redeploy FireFly compose (overwrite containers)' -ForegroundColor Cyan

if (-not $SkipStopLocal -and (Test-Path -LiteralPath $StopAll)) {
  Write-Host '==> Stop local start-all processes' -ForegroundColor Cyan
  & $StopAll
}

$downArgs = @('compose', '-f', $ComposeFile, '--env-file', $EnvFile, 'down', '--remove-orphans')
if ($WipeVolumes) {
  Write-Host 'WARN: -WipeVolumes will delete mysql-data / media-data' -ForegroundColor Yellow
  $downArgs += '-v'
}
& docker @downArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose down failed (exit=$LASTEXITCODE)" }

$upArgs = @('compose', '-f', $ComposeFile, '--env-file', $EnvFile, 'up', '-d', '--force-recreate', '--remove-orphans')
if (-not $NoBuild) { $upArgs += '--build' }
Write-Host '==> docker compose up (build + force-recreate)' -ForegroundColor Cyan
& docker @upArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed (exit=$LASTEXITCODE)" }

Write-Host '==> Wait for MySQL healthy' -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(3)
$health = $null
do {
  Start-Sleep -Seconds 3
  $health = docker inspect --format '{{.State.Health.Status}}' firefly-mysql-1 2>$null
  if ($health -eq 'healthy') { break }
} while ((Get-Date) -lt $deadline)
if ($health -ne 'healthy') {
  throw "MySQL not healthy (status=$health)"
}

Write-Host '==> Ensure market database + schema' -ForegroundColor Cyan
& docker exec -i firefly-mysql-1 mysql -uroot "-p$($env:MYSQL_PASSWORD)" -e "CREATE DATABASE IF NOT EXISTS ``market`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ($LASTEXITCODE -ne 0) { throw 'CREATE DATABASE market failed' }
if (Test-Path -LiteralPath $SchemaSql) {
  Get-Content -LiteralPath $SchemaSql -Raw | & docker exec -i firefly-mysql-1 mysql -uroot "-p$($env:MYSQL_PASSWORD)"
  if ($LASTEXITCODE -ne 0) { throw 'apply market schema.sql failed' }
}

Write-Host ''
Write-Host 'Redeploy done.' -ForegroundColor Green
Write-Host '  Gateway health: http://localhost:8080/gateway/health'
Write-Host '  Frontend (local): cd web-console; npm run dev'
Write-Host '  Seed market: .\deploy\scripts\seed-market.ps1'
