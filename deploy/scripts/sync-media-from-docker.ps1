# 将 compose 媒体卷 firefly_media-data 合并到本机 MEDIA_STORAGE_DIR（不覆盖已有同名文件）
# 用法（仓库根目录）：
#   .\deploy\scripts\sync-media-from-docker.ps1
#   .\deploy\scripts\sync-media-from-docker.ps1 -TargetDir "D:/FireFlyData/media"

param(
    [string]$TargetDir = ''
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path
if (-not $TargetDir) {
    $envFile = Join-Path $RepoRoot '.env'
    if (Test-Path -LiteralPath $envFile) {
        foreach ($raw in Get-Content -LiteralPath $envFile) {
            $line = $raw.Trim()
            if ($line -match '^MEDIA_STORAGE_DIR=(.+)$') {
                $TargetDir = $Matches[1].Trim()
                break
            }
        }
    }
    if (-not $TargetDir) {
        $TargetDir = Join-Path $RepoRoot '.firefly-dev\media'
    }
}

if (-not (Test-Path -LiteralPath $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
}

$vol = 'firefly_media-data'
$null = docker volume inspect $vol 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker volume $vol not found (skip)." -ForegroundColor Yellow
    exit 0
}

$dockerPath = ($TargetDir -replace '\\', '/')
Write-Host "Sync $vol -> $TargetDir"
docker run --rm -v "${vol}:/from:ro" -v "${dockerPath}:/to" alpine sh -c "cp -an /from/. /to/ 2>/dev/null || true"
$count = (Get-ChildItem -LiteralPath $TargetDir -File -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Host "Done. Local media files: $count" -ForegroundColor Green
