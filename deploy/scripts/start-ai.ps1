# 本地启动三个 AI 服务（骨架：仅 /health）
# 用法（仓库根目录）：.\deploy\scripts\start-ai.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..", "..")).Path
$AiRoot = Join-Path $RepoRoot "services-ai"
$EnvFile = Join-Path $AiRoot ".env"
if (-not (Test-Path -LiteralPath $EnvFile)) {
    $EnvFile = Join-Path $RepoRoot ".env"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
    Write-Host "请先配置 services-ai\.env 或仓库根 .env（含 RABBITMQ_PASSWORD、INTERNAL_HMAC_SECRET）" -ForegroundColor Yellow
    exit 1
}

function Import-DotEnv([string]$Path) {
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        Set-Item -Path "Env:$key" -Value $val
    }
}

Import-DotEnv $EnvFile
Set-Location $AiRoot

$services = @(
    @{ Name = "moderation-service"; Module = "moderation_service.main"; Port = 9101; Env = "MODERATION_SERVICE_PORT" },
    @{ Name = "tagging-service"; Module = "tagging_service.main"; Port = 9102; Env = "TAGGING_SERVICE_PORT" },
    @{ Name = "recommend-service"; Module = "recommend_service.main"; Port = 9103; Env = "RECOMMEND_SERVICE_PORT" }
)

Write-Host "启动 FireFly AI 服务（骨架）..." -ForegroundColor Cyan

foreach ($svc in $services) {
    $port = [int](Get-Item -Path "Env:$($svc.Env)" -ErrorAction SilentlyContinue).Value
    if (-not $port) { $port = $svc.Port }
    Write-Host "  -> $($svc.Name) :$port"
    Start-Process -FilePath "python" -ArgumentList "-m", $svc.Module `
        -WorkingDirectory $AiRoot -WindowStyle Normal
}

Write-Host ""
Write-Host "已在新窗口启动三个进程。健康检查示例：" -ForegroundColor Green
Write-Host "  curl.exe http://127.0.0.1:9101/health"
Write-Host "  curl.exe http://127.0.0.1:9102/health"
Write-Host "  curl.exe http://127.0.0.1:9103/health"
