#Requires -Version 5.1
<#
.SYNOPSIS
  Seed realistic notes via Gateway (multi-author + optional covers).

.EXAMPLE
  .\deploy\scripts\seed-notes.ps1
  .\deploy\scripts\seed-notes.ps1 -NotesPerAuthor 15 -WithCovers:$false
#>
param(
  [string]$GatewayBase = 'http://127.0.0.1:8080',
  [string]$Password = '123456',
  [int]$NotesPerAuthor = 12,
  [switch]$NoCovers,
  [string]$DeviceFingerprint = 'seed-notes-device-fingerprint-01',
  [string]$DataFile = ''
)

$ErrorActionPreference = 'Stop'
if (-not $DataFile) {
  $DataFile = Join-Path $PSScriptRoot 'seed-notes-data.json'
}

function Write-Utf8JsonFile([string]$Path, [object]$Object) {
  $json = $Object | ConvertTo-Json -Compress -Depth 8
  [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

function Invoke-JsonApi {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [string]$IdempotencyKey = $null
  )
  $tmpIn = Join-Path $env:TEMP ("ff-seed-in-{0}.json" -f [guid]::NewGuid().ToString('N'))
  $tmpOut = Join-Path $env:TEMP ("ff-seed-out-{0}.json" -f [guid]::NewGuid().ToString('N'))
  try {
    $curlArgs = @('-s', '-X', $Method, $Url, '-H', 'Content-Type: application/json; charset=utf-8', '-o', $tmpOut)
    foreach ($k in $Headers.Keys) {
      $curlArgs += @('-H', ("{0}: {1}" -f $k, $Headers[$k]))
    }
    if ($IdempotencyKey) {
      $curlArgs += @('-H', ("Idempotency-Key: {0}" -f $IdempotencyKey))
    }
    if ($null -ne $Body) {
      Write-Utf8JsonFile $tmpIn $Body
      $curlArgs += @('--data-binary', "@$tmpIn")
    }
    $null = & curl.exe @curlArgs
    if (-not (Test-Path -LiteralPath $tmpOut) -or ((Get-Item -LiteralPath $tmpOut).Length -le 0)) {
      throw "empty response: $Method $Url"
    }
    $raw = [IO.File]::ReadAllText($tmpOut, [Text.UTF8Encoding]::new($false))
    return $raw | ConvertFrom-Json
  } finally {
    Remove-Item -LiteralPath $tmpIn, $tmpOut -ErrorAction SilentlyContinue
  }
}

function Ensure-User([string]$Username, [string]$Nickname) {
  $login = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/user/login" -Body @{
    username          = $Username
    password          = $Password
    deviceFingerprint = $DeviceFingerprint
  }
  if ($login.code -eq 0 -and ($login.data.accessToken -or $login.data.token)) {
    return $login.data
  }

  $reg = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/user/register" -Body @{
    username          = $Username
    password          = $Password
    nickname          = $Nickname
    deviceFingerprint = $DeviceFingerprint
  }
  if ($reg.code -eq 0 -and ($reg.data.accessToken -or $reg.data.token)) {
    return $reg.data
  }

  $login2 = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/user/login" -Body @{
    username          = $Username
    password          = $Password
    deviceFingerprint = $DeviceFingerprint
  }
  if ($login2.code -ne 0) {
    throw ("cannot auth {0}: reg={1}; login={2}" -f $Username, $reg.message, $login2.message)
  }
  return $login2.data
}

function New-CoverJpeg([string]$OutPath, [string]$SeedText) {
  $ok = $false
  try {
    & curl.exe -sL --max-time 15 -o $OutPath ("https://picsum.photos/seed/{0}/720/900" -f $SeedText) | Out-Null
    if ((Test-Path $OutPath) -and ((Get-Item $OutPath).Length -gt 2000)) {
      $ok = $true
    }
  } catch {}

  if (-not $ok) {
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap 720, 900
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hash = [Math]::Abs($SeedText.GetHashCode())
    $c = [System.Drawing.Color]::FromArgb(
      255,
      80 + ($hash % 140),
      90 + ([int]($hash / 7) % 120),
      110 + ([int]($hash / 13) % 100)
    )
    $g.Clear($c)
    $font = New-Object System.Drawing.Font 'Arial', 28
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $g.DrawString($SeedText, $font, $brush, 40, 400)
    $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    $g.Dispose(); $bmp.Dispose(); $font.Dispose(); $brush.Dispose()
  }
}

function Upload-Cover([string]$Token, [string]$JpegPath) {
  $tmpOut = Join-Path $env:TEMP ("ff-seed-up-{0}.json" -f [guid]::NewGuid().ToString('N'))
  try {
    $null = & curl.exe -s -X POST "$GatewayBase/api/media/upload" `
      -H ("Authorization: Bearer {0}" -f $Token) `
      -F ("file=@{0};type=image/jpeg" -f $JpegPath) `
      -o $tmpOut
    $raw = [IO.File]::ReadAllText($tmpOut, [Text.UTF8Encoding]::new($false))
    $res = $raw | ConvertFrom-Json
    if ($res.code -ne 0) {
      Write-Warning ("cover upload failed: {0}" -f $res.message)
      return $null
    }
    return [string]$res.data.url
  } finally {
    Remove-Item -LiteralPath $tmpOut -ErrorAction SilentlyContinue
  }
}

$WithCovers = -not $NoCovers.IsPresent

if (-not (Test-Path -LiteralPath $DataFile)) {
  throw "data file not found: $DataFile"
}

$data = Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json
$authors = @($data.authors)
$pool = @($data.notes)

Write-Host ("Gateway: {0}" -f $GatewayBase)
Write-Host ("Authors: {0}, notes/author: {1}, covers: {2}" -f $authors.Count, $NotesPerAuthor, $WithCovers)

$sessions = @()
foreach ($a in $authors) {
  Write-Host ("ensure user {0} ({1}) ..." -f $a.u, $a.n)
  $auth = Ensure-User -Username $a.u -Nickname $a.n
  $token = $auth.accessToken
  if (-not $token) { $token = $auth.token }
  if (-not $token) { throw ("no token for {0}" -f $a.u) }
  $sessions += @{ u = $a.u; n = $a.n; token = $token; id = $auth.user.id }
}

Write-Host 'create follow edges...'
for ($i = 0; $i -lt $sessions.Count; $i++) {
  $from = $sessions[$i]
  for ($j = 0; $j -lt $sessions.Count; $j++) {
    if ($i -eq $j) { continue }
    if ((($i + $j) % 3) -ne 0) { continue }
    $to = $sessions[$j]
    try {
      $null = Invoke-JsonApi -Method POST -Url ("{0}/api/user/follow/{1}" -f $GatewayBase, $to.id) -Headers @{
        Authorization = ("Bearer {0}" -f $from.token)
      }
    } catch {
      Write-Warning ("follow failed {0}->{1}: {2}" -f $from.u, $to.u, $_)
    }
  }
}

$rnd = [Random]::new(42)
$okCount = 0
$failCount = 0
$coverDir = Join-Path $env:TEMP 'ff-seed-covers'
New-Item -ItemType Directory -Force -Path $coverDir | Out-Null
$total = $authors.Count * $NotesPerAuthor
$idx = 0

foreach ($s in $sessions) {
  $shuffled = @($pool | Sort-Object { $rnd.Next() })
  $picked = New-Object System.Collections.Generic.List[object]
  for ($n = 0; $n -lt $NotesPerAuthor; $n++) {
    $base = $shuffled[$n % $shuffled.Count]
    if ($n -lt $shuffled.Count) {
      $picked.Add($base) | Out-Null
    } else {
      $picked.Add([pscustomobject]@{
          t = ("{0} ({1})" -f $base.t, ($n + 1))
          c = ("{0}`n`n[follow-up] Feeling good today, jotting this down too." -f $base.c)
        }) | Out-Null
    }
  }

  foreach ($note in $picked) {
    $idx++
    $coverUrl = $null
    if ($WithCovers) {
      $jpg = Join-Path $coverDir ("cover-{0}.jpg" -f $idx)
      try {
        New-CoverJpeg -OutPath $jpg -SeedText ("ff{0}{1}" -f $s.u, $idx)
        $coverUrl = Upload-Cover -Token $s.token -JpegPath $jpg
      } catch {
        Write-Warning ("cover error #{0}: {1}" -f $idx, $_)
      }
    }

    $title = [string]$note.t
    if ($title.Length -gt 40) { $title = $title.Substring(0, 40) }

    $body = @{
      title   = $title
      content = [string]$note.c
    }
    if ($coverUrl) {
      $body.coverUrl = $coverUrl
      $body.mediaUrls = @($coverUrl)
    }

    try {
      $res = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/note" `
        -Headers @{ Authorization = ("Bearer {0}" -f $s.token) } `
        -IdempotencyKey ([guid]::NewGuid().ToString()) `
        -Body $body
      if ($res.code -eq 0) {
        $okCount++
        Write-Host ("[{0}/{1}] OK @{2}  {3}" -f $idx, $total, $s.u, $title)
      } else {
        $failCount++
        Write-Warning ("[{0}] FAIL @{1}: {2}" -f $idx, $s.u, $res.message)
      }
    } catch {
      $failCount++
      Write-Warning ("[{0}] ERR @{1}: {2}" -f $idx, $s.u, $_)
    }
  }
}

Write-Host ''
Write-Host ("Done. success={0} fail={1}" -f $okCount, $failCount)
Write-Host ("Password for all seed users: {0}" -f $Password)
Write-Host 'Accounts: seed_momo / seed_yoyo / seed_kira / seed_bean / seed_lin / seed_fit / seed_home / seed_skincare'
