# 将本机 MEDIA_STORAGE_DIR 合并到 compose 媒体卷 firefly_media-data（跳过卷内已有同名文件）
# 用法（仓库根目录）：
#   .\deploy\scripts\sync-media-to-docker.ps1
#   .\deploy\scripts\sync-media-to-docker.ps1 -SourceDir "D:/FireFlyData/media"

param(
    [string]$SourceDir = ''
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if (-not $SourceDir) {
    $envFile = Join-Path $RepoRoot '.env'
    if (Test-Path -LiteralPath $envFile) {
        foreach ($raw in Get-Content -LiteralPath $envFile) {
            $line = $raw.Trim()
            if ($line -match '^MEDIA_STORAGE_DIR=(.+)$') {
                $SourceDir = $Matches[1].Trim().Trim('"').Trim("'")
                break
            }
        }
    }
    if (-not $SourceDir) {
        $SourceDir = Join-Path $RepoRoot '.firefly-dev\media'
    }
}

if (-not (Test-Path -LiteralPath $SourceDir)) {
    Write-Host "Source not found: $SourceDir (skip)." -ForegroundColor Yellow
    exit 0
}

$vol = 'firefly_media-data'
$null = docker volume inspect $vol 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker volume $vol not found (skip)." -ForegroundColor Yellow
    exit 0
}

$dockerPath = ($SourceDir -replace '\\', '/')
$localCount = (Get-ChildItem -LiteralPath $SourceDir -File -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Host "Sync $SourceDir ($localCount files) -> $vol"

$tmpSh = Join-Path $env:TEMP ("ff-sync-media-{0}.sh" -f [guid]::NewGuid().ToString('N'))
$shBody = @(
    '#!/bin/sh'
    'set -e'
    'for f in /from/*; do'
    '  [ -f "$f" ] || continue'
    '  base=$(basename "$f")'
    '  if [ ! -e "/to/$base" ]; then'
    '    cp -f "$f" "/to/$base"'
    '  fi'
    'done'
) -join "`n"
[IO.File]::WriteAllText($tmpSh, $shBody + "`n", [Text.UTF8Encoding]::new($false))
$tmpShDocker = ($tmpSh -replace '\\', '/')

try {
    docker run --rm `
        -v "${dockerPath}:/from:ro" `
        -v "${vol}:/to" `
        -v "${tmpShDocker}:/sync.sh:ro" `
        alpine sh /sync.sh
    if ($LASTEXITCODE -ne 0) { throw 'sync to docker volume failed' }
} finally {
    Remove-Item -LiteralPath $tmpSh -ErrorAction SilentlyContinue
}

$volCount = docker run --rm -v "${vol}:/m:ro" alpine sh -c "ls -1 /m 2>/dev/null | wc -l"
Write-Host "Done. Volume media files: $($volCount.Trim())" -ForegroundColor Green
