#Requires -Version 5.1
<#
.SYNOPSIS
  Ensure note lat/lng/place_name columns and scatter published notes around Nanjing.

.EXAMPLE
  .\deploy\scripts\seed-nearby-nanjing.ps1
#>
param(
  [string]$MysqlContainer = 'firefly-mysql-1',
  [int]$MaxNotes = 60,
  [string]$DataFile = ''
)

$ErrorActionPreference = 'Continue'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $DataFile) {
  $DataFile = Join-Path $PSScriptRoot 'seed-nearby-nanjing-data.json'
}

Get-Content (Join-Path $RepoRoot '.env') -ErrorAction SilentlyContinue | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $i = $line.IndexOf('=')
  if ($i -lt 1) { return }
  $k = $line.Substring(0, $i).Trim()
  $v = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
  Set-Item -Path "Env:$k" -Value $v
}

if (-not $env:MYSQL_PASSWORD) {
  throw 'MYSQL_PASSWORD missing'
}

function Invoke-MysqlFile([string]$Sql) {
  $tmp = Join-Path $env:TEMP ("ff-nearby-{0}.sql" -f [guid]::NewGuid().ToString('N'))
  [IO.File]::WriteAllText($tmp, $Sql, [Text.UTF8Encoding]::new($false))
  try {
    docker cp $tmp "${MysqlContainer}:/tmp/ff-nearby.sql" | Out-Null
    docker exec $MysqlContainer mysql -uroot "-p$env:MYSQL_PASSWORD" --default-character-set=utf8mb4 -e "source /tmp/ff-nearby.sql" 2>$null
  } finally {
    Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
  }
}

$data = Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json
$spots = @($data.spots)
if ($spots.Count -lt 1) { throw 'no spots in data file' }

Write-Host '==> ensure columns'
$out = docker exec $MysqlContainer mysql -uroot "-p$env:MYSQL_PASSWORD" -N -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='content' AND TABLE_NAME='note' AND COLUMN_NAME='lat';" 2>$null
$count = 0
[void][int]::TryParse(("$out").Trim(), [ref]$count)
if ($count -lt 1) {
  Invoke-MysqlFile @"
USE content;
ALTER TABLE note
  ADD COLUMN lat DOUBLE NULL COMMENT 'lat WGS84' AFTER cover_url,
  ADD COLUMN lng DOUBLE NULL COMMENT 'lng WGS84' AFTER lat,
  ADD COLUMN place_name VARCHAR(64) NULL COMMENT 'place' AFTER lng;
"@
  Write-Host 'columns added'
} else {
  Write-Host 'columns already exist'
}

Write-Host ("==> geo-tag up to {0} published notes" -f $MaxNotes)
$idLines = docker exec $MysqlContainer mysql -uroot "-p$env:MYSQL_PASSWORD" -N -e "SELECT id FROM content.note WHERE status=1 ORDER BY id DESC LIMIT $MaxNotes;" 2>$null
$ids = @()
if ($idLines) {
  $ids = @($idLines -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^\d+$' })
}
Write-Host ("notes={0}" -f $ids.Count)

$rnd = [Random]::new(32)
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('USE content;')
$i = 0
foreach ($id in $ids) {
  $spot = $spots[$i % $spots.Count]
  $dLat = ($rnd.NextDouble() - 0.5) * 0.02
  $dLng = ($rnd.NextDouble() - 0.5) * 0.02
  $lat = [Math]::Round([double]$spot.lat + $dLat, 6)
  $lng = [Math]::Round([double]$spot.lng + $dLng, 6)
  $name = ([string]$spot.name).Replace("'", "''")
  [void]$sb.AppendLine(("UPDATE note SET lat={0}, lng={1}, place_name='{2}' WHERE id={3};" -f $lat, $lng, $name, $id))
  $i++
}
Invoke-MysqlFile $sb.ToString()
Write-Host 'Done. GET /api/feed/nearby?lat=32.041544&lng=118.784485&radiusKm=8'
