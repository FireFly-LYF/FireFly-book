# 将 MySQL 已有笔记灌入 Elasticsearch
# 用法（仓库根目录）：.\deploy\scripts\reindex-es.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot "..") "..")).Path
$EnvFile = Join-Path $RepoRoot ".env"
if (Test-Path -LiteralPath $EnvFile) {
    foreach ($raw in Get-Content -LiteralPath $EnvFile) {
        $line = $raw.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { continue }
        Set-Item -Path "Env:$($line.Substring(0, $idx).Trim())" -Value $line.Substring($idx + 1).Trim()
    }
}
$pass = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "123456" }
Set-Location (Join-Path $RepoRoot "services-ai")
python (Join-Path $RepoRoot "deploy\scripts\reindex_es.py") $pass "http://127.0.0.1:9007"
