#Requires -Version 5.1
<#
.SYNOPSIS
  Seed many mock market products (multi-seller + optional covers).

.EXAMPLE
  .\deploy\scripts\seed-market.ps1
  .\deploy\scripts\seed-market.ps1 -ProductsPerSeller 20 -NoCovers
#>
param(
  [string]$GatewayBase = 'http://127.0.0.1:8080',
  [string]$Password = '123456',
  [int]$ProductsPerSeller = 16,
  [switch]$NoCovers,
  [string]$DeviceFingerprint = 'seed-market-device-fingerprint-01',
  [string]$DataFile = '',
  [string]$Token = ''
)

$ErrorActionPreference = 'Stop'
if (-not $DataFile) {
  $DataFile = Join-Path $PSScriptRoot 'seed-market-data.json'
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
  $tmpIn = Join-Path $env:TEMP ("ff-mseed-in-{0}.json" -f [guid]::NewGuid().ToString('N'))
  $tmpOut = Join-Path $env:TEMP ("ff-mseed-out-{0}.json" -f [guid]::NewGuid().ToString('N'))
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

function Get-LocalCoverPool {
  $dir = Join-Path $PSScriptRoot 'seed-covers'
  if (-not (Test-Path -LiteralPath $dir)) { return @() }
  return @(Get-ChildItem -LiteralPath $dir -Filter '*.jpg' -File | Sort-Object Name)
}

function New-CoverJpeg([string]$OutPath, [string]$SeedText) {
  $pool = Get-LocalCoverPool
  if ($pool.Count -gt 0) {
    $hash = [Math]::Abs($SeedText.GetHashCode())
    $src = $pool[$hash % $pool.Count].FullName
    Copy-Item -LiteralPath $src -Destination $OutPath -Force
    if ((Test-Path -LiteralPath $OutPath) -and ((Get-Item -LiteralPath $OutPath).Length -gt 8000)) {
      return
    }
  }

  try {
    & curl.exe -sL --max-time 12 -o $OutPath ("https://picsum.photos/seed/{0}/720/900" -f $SeedText) | Out-Null
    if ((Test-Path -LiteralPath $OutPath) -and ((Get-Item -LiteralPath $OutPath).Length -gt 8000)) {
      return
    }
  } catch {}

  Add-Type -AssemblyName System.Drawing
  $bmp = New-Object System.Drawing.Bitmap 720, 900
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $hash = [Math]::Abs($SeedText.GetHashCode())
  $r1 = 60 + ($hash % 120)
  $g1 = 80 + (([int]($hash / 5)) % 100)
  $b1 = 120 + (([int]($hash / 11)) % 80)
  $r2 = 140 + ($hash % 80)
  $g2 = 100 + (([int]($hash / 3)) % 90)
  $b2 = 90 + (([int]($hash / 17)) % 100)
  $c1 = [System.Drawing.Color]::FromArgb(255, $r1, $g1, $b1)
  $c2 = [System.Drawing.Color]::FromArgb(255, $r2, $g2, $b2)
  $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush (
    (New-Object System.Drawing.Point 0, 0),
    (New-Object System.Drawing.Point 720, 900),
    $c1, $c2
  )
  $g.FillRectangle($brush, 0, 0, 720, 900)
  $rnd = [Random]::new($hash)
  for ($i = 0; $i -lt 40; $i++) {
    $alpha = 18 + $rnd.Next(40)
    $spot = [System.Drawing.Color]::FromArgb($alpha, $rnd.Next(255), $rnd.Next(255), $rnd.Next(255))
    $sb = New-Object System.Drawing.SolidBrush $spot
    $g.FillEllipse($sb, $rnd.Next(-40, 680), $rnd.Next(-40, 860), 40 + $rnd.Next(180), 40 + $rnd.Next(180))
    $sb.Dispose()
  }
  $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Jpeg)
  $brush.Dispose(); $g.Dispose(); $bmp.Dispose()
}

function Upload-Cover([string]$AuthToken, [string]$JpegPath) {
  $tmpOut = Join-Path $env:TEMP ("ff-mseed-up-{0}.json" -f [guid]::NewGuid().ToString('N'))
  try {
    $null = & curl.exe -s -X POST "$GatewayBase/api/media/upload" `
      -H ("Authorization: Bearer {0}" -f $AuthToken) `
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
$sellers = @($data.sellers)
$pool = @($data.products)

Write-Host ("Gateway: {0}" -f $GatewayBase)
Write-Host ("Sellers: {0}, products/seller: {1}, covers: {2}" -f $sellers.Count, $ProductsPerSeller, $WithCovers)

$sessions = @()
if ($Token) {
  Write-Host 'using provided Token as single seller...'
  $sessions += @{ u = 'token-seller'; n = 'token-seller'; token = $Token }
} else {
  foreach ($a in $sellers) {
    Write-Host ("ensure seller {0} ({1}) ..." -f $a.u, $a.n)
    $auth = Ensure-User -Username $a.u -Nickname $a.n
    $tok = $auth.accessToken
    if (-not $tok) { $tok = $auth.token }
    if (-not $tok) { throw ("no token for {0}" -f $a.u) }
    $sessions += @{ u = $a.u; n = $a.n; token = $tok }
  }
}

$rnd = [Random]::new(20261021)
$okCount = 0
$failCount = 0
$coverDir = Join-Path $env:TEMP 'ff-seed-market-covers'
New-Item -ItemType Directory -Force -Path $coverDir | Out-Null
$total = $sessions.Count * $ProductsPerSeller
$idx = 0

foreach ($s in $sessions) {
  $shuffled = @($pool | Sort-Object { $rnd.Next() })
  $picked = New-Object System.Collections.Generic.List[object]
  for ($n = 0; $n -lt $ProductsPerSeller; $n++) {
    $base = $shuffled[$n % $shuffled.Count]
    if ($n -lt $shuffled.Count) {
      $picked.Add($base) | Out-Null
    } else {
      $suffix = $n + 1
      $picked.Add([pscustomobject]@{
          t = ("{0} #{1}" -f $base.t, $suffix)
          d = ("{0} (restock {1})" -f $base.d, $suffix)
          p = [int]$base.p + ($rnd.Next(-300, 501))
          s = [Math]::Max(5, [int]$base.s + $rnd.Next(-10, 21))
          cat = $base.cat
        }) | Out-Null
    }
  }

  foreach ($prod in $picked) {
    $idx++
    $coverUrl = $null
    if ($WithCovers) {
      $jpg = Join-Path $coverDir ("mcover-{0}.jpg" -f $idx)
      try {
        New-CoverJpeg -OutPath $jpg -SeedText ("mk{0}{1}" -f $s.u, $idx)
        $coverUrl = Upload-Cover -AuthToken $s.token -JpegPath $jpg
      } catch {
        Write-Warning ("cover error #{0}: {1}" -f $idx, $_)
      }
    }

    $title = [string]$prod.t
    if ($title.Length -gt 60) { $title = $title.Substring(0, 60) }
    $price = [int]$prod.p
    if ($price -lt 100) { $price = 100 }
    $stock = [int]$prod.s
    if ($stock -lt 1) { $stock = 1 }

    $body = @{
      title       = $title
      description = [string]$prod.d
      priceCents  = $price
      stock       = $stock
    }
    if ($coverUrl) {
      $body.coverUrl = $coverUrl
    }

    try {
      $res = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/market/products" `
        -Headers @{ Authorization = ("Bearer {0}" -f $s.token) } `
        -IdempotencyKey ([guid]::NewGuid().ToString('N').Substring(0, 16)) `
        -Body $body
      if ($res.code -eq 0) {
        $okCount++
        $yuan = [Math]::Round($price / 100.0, 2)
        Write-Host ("[{0}/{1}] OK @{2}  ¥{3}  {4}" -f $idx, $total, $s.u, $yuan, $title)
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
if (-not $Token) {
  Write-Host ("Password for all seed sellers: {0}" -f $Password)
  Write-Host 'Accounts: seed_momo / seed_yoyo / seed_kira / seed_bean / seed_lin / seed_fit / seed_home / seed_skincare'
}
