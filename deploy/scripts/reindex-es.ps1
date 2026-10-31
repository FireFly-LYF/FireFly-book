#Requires -Version 5.1
<#
.SYNOPSIS
  将 MySQL 已发布笔记与用户回填到 Elasticsearch（经 search-service :9007 内部接口）。

.EXAMPLE
  .\deploy\scripts\reindex-es.ps1
  .\deploy\scripts\reindex-es.ps1 -SearchBase http://127.0.0.1:9007
#>
param(
  [string]$SearchBase = 'http://127.0.0.1:9007'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$EnvFile = Join-Path $RepoRoot '.env'

function Import-DotEnv([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) { return }
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

function Get-HmacSign([string]$Secret, [string]$UserId, [long]$UnixSec) {
  $payload = "{0}|{1}" -f $UserId, $UnixSec
  $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
  try {
    $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload))
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
  } finally {
    $hmac.Dispose()
  }
}

function Invoke-InnerPost([string]$Path, [hashtable]$Body) {
  $tmpIn = Join-Path $env:TEMP ("ff-reidx-in-{0}.json" -f [guid]::NewGuid().ToString('N'))
  $tmpOut = Join-Path $env:TEMP ("ff-reidx-out-{0}.json" -f [guid]::NewGuid().ToString('N'))
  try {
    $json = $Body | ConvertTo-Json -Compress -Depth 6
    [IO.File]::WriteAllText($tmpIn, $json, [Text.UTF8Encoding]::new($false))
    $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $sig = Get-HmacSign -Secret $env:INTERNAL_HMAC_SECRET -UserId '' -UnixSec $ts
    $null = & curl.exe -s -X POST ("{0}{1}" -f $SearchBase.TrimEnd('/'), $Path) `
      -H 'Content-Type: application/json; charset=utf-8' `
      -H ("X-Gateway-Ts: {0}" -f $ts) `
      -H ("X-Gateway-Sign: {0}" -f $sig) `
      --data-binary "@$tmpIn" `
      -o $tmpOut
    if (-not (Test-Path -LiteralPath $tmpOut) -or ((Get-Item -LiteralPath $tmpOut).Length -le 0)) {
      throw "empty response $Path"
    }
    return (Get-Content -LiteralPath $tmpOut -Raw -Encoding UTF8 | ConvertFrom-Json)
  } finally {
    Remove-Item -LiteralPath $tmpIn, $tmpOut -ErrorAction SilentlyContinue
  }
}

function Invoke-MysqlQuery([string]$Sql) {
  $args = @(
    'exec', 'firefly-mysql-1', 'mysql',
    '--default-character-set=utf8mb4',
    '-uroot', "-p$($env:MYSQL_PASSWORD)",
    '-N', '-B', '-e', $Sql
  )
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $out = & docker @args 2>$null
  $ErrorActionPreference = $prev
  if ($LASTEXITCODE -ne 0) { throw "mysql query failed: $Sql" }
  return @($out)
}

Import-DotEnv $EnvFile
if (-not $env:MYSQL_PASSWORD) { throw 'MYSQL_PASSWORD missing in .env' }
if (-not $env:INTERNAL_HMAC_SECRET) { throw 'INTERNAL_HMAC_SECRET missing in .env' }

Write-Host "Search: $SearchBase" -ForegroundColor Cyan
Write-Host '==> Index published notes (status=1)' -ForegroundColor Cyan
$noteLines = Invoke-MysqlQuery 'SELECT id,user_id,title,content,cover_url FROM content.note WHERE status=1'
$ok = 0; $fail = 0
foreach ($line in $noteLines) {
  if ([string]::IsNullOrWhiteSpace($line)) { continue }
  $parts = $line -split "`t", 5
  if ($parts.Count -lt 5) { continue }
  $cover = $parts[4]
  if ($cover -eq 'NULL' -or $cover -eq '\N') { $cover = $null }
  try {
    $res = Invoke-InnerPost '/api/search/inner/index' @{
      id       = [long]$parts[0]
      userId   = [long]$parts[1]
      title    = [string]$parts[2]
      content  = [string]$parts[3]
      coverUrl = $cover
    }
    if ($res.code -eq 0) { $ok++ } else { $fail++; Write-Warning ("note {0}: {1}" -f $parts[0], $res.message) }
  } catch {
    $fail++
    Write-Warning ("note {0}: {1}" -f $parts[0], $_)
  }
}
Write-Host ("notes indexed={0} failed={1}" -f $ok, $fail)

Write-Host '==> Index users' -ForegroundColor Cyan
$userLines = Invoke-MysqlQuery 'SELECT id,username,nickname,avatar_url FROM user.user'
$uOk = 0; $uFail = 0
foreach ($line in $userLines) {
  if ([string]::IsNullOrWhiteSpace($line)) { continue }
  $parts = $line -split "`t", 4
  if ($parts.Count -lt 3) { continue }
  $avatar = if ($parts.Count -ge 4) { $parts[3] } else { $null }
  if ($avatar -eq 'NULL' -or $avatar -eq '\N') { $avatar = $null }
  try {
    $res = Invoke-InnerPost '/api/search/inner/index-user' @{
      id        = [long]$parts[0]
      username  = [string]$parts[1]
      nickname  = [string]$parts[2]
      avatarUrl = $avatar
    }
    if ($res.code -eq 0) { $uOk++ } else { $uFail++; Write-Warning ("user {0}: {1}" -f $parts[0], $res.message) }
  } catch {
    $uFail++
    Write-Warning ("user {0}: {1}" -f $parts[0], $_)
  }
}
Write-Host ("users indexed={0} failed={1}" -f $uOk, $uFail)

if ($fail -gt 0 -or $uFail -gt 0) { exit 1 }
Write-Host 'Done.' -ForegroundColor Green
