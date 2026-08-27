# Start AI services locally (Python, no Docker image pull)
# Usage from repo root: .\deploy\scripts\start-ai.ps1
# Params: -AssistantOnly  -SkipInstall

param(
    [switch]$AssistantOnly,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")).Path
$AiRoot = Join-Path $RepoRoot "services-ai"
$EnvFile = Join-Path $AiRoot ".env"
if (-not (Test-Path -LiteralPath $EnvFile)) {
    $EnvFile = Join-Path $RepoRoot ".env"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
    Write-Host "Missing .env (need INTERNAL_HMAC_SECRET). Copy services-ai\.env.example" -ForegroundColor Yellow
    exit 1
}

function Import-DotEnv([string]$Path) {
    foreach ($raw in Get-Content -LiteralPath $Path) {
        $line = $raw.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        Set-Item -Path "Env:$key" -Value $val
    }
}

Import-DotEnv $EnvFile

if (-not $env:SEARCH_BASE_URL) {
    Set-Item -Path Env:SEARCH_BASE_URL -Value "http://127.0.0.1:9007"
}

Set-Location $AiRoot

$pipIndex = "https://pypi.tuna.tsinghua.edu.cn/simple"
if (-not $SkipInstall) {
    Write-Host "Installing Python packages..." -ForegroundColor Cyan
    $pkgs = @("./firefly-ai-common", "./assistant-service")
    if (-not $AssistantOnly) { $pkgs += "./moderation-service" }
    foreach ($pkg in $pkgs) {
        python -m pip install -e $pkg -i $pipIndex -q
    }
}

$services = @(
    @{ Name = "assistant-service"; Module = "assistant_service.main"; Port = 9102; Env = "ASSISTANT_SERVICE_PORT" }
)
if (-not $AssistantOnly) {
    $services = @(
        @{ Name = "moderation-service"; Module = "moderation_service.main"; Port = 9101; Env = "MODERATION_SERVICE_PORT" }
    ) + $services
}

Write-Host "Starting FireFly AI services..." -ForegroundColor Cyan

foreach ($svc in $services) {
    $port = [int](Get-Item -Path "Env:$($svc.Env)" -ErrorAction SilentlyContinue).Value
    if (-not $port) { $port = $svc.Port }
    Write-Host "  -> $($svc.Name) :$port"
    Start-Process -FilePath "python" -ArgumentList "-m", $svc.Module `
        -WorkingDirectory $AiRoot -WindowStyle Normal
}

Write-Host ""
Write-Host "Health check:" -ForegroundColor Green
if (-not $AssistantOnly) {
    Write-Host "  curl.exe http://127.0.0.1:9101/health"
}
Write-Host "  curl.exe http://127.0.0.1:9102/health"
