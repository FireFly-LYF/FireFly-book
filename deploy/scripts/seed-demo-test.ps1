#Requires -Version 5.1
<#
.SYNOPSIS
  Prepare demo account test/test: profile, follow seed authors, notes, likes/collects, market address.
  Run seed-notes.ps1 first (and moderation so notes are published).

.EXAMPLE
  .\deploy\scripts\seed-demo-test.ps1
  .\deploy\scripts\seed-demo-test.ps1 -SkipNotes -SkipMarket
#>
param(
  [string]$GatewayBase = 'http://127.0.0.1:8080',
  [string]$DemoUser = 'test',
  [string]$DemoPassword = 'test',
  [string]$SeedPassword = '123456',
  [string]$DeviceFingerprint = 'seed-demo-test-device-01',
  [string]$AuthorsFile = '',
  [string]$DataFile = '',
  [switch]$SkipNotes,
  [switch]$SkipLikes,
  [switch]$SkipMarket,
  [switch]$ForceApproveViaMysql,
  [string]$MysqlContainer = 'firefly-mysql-1'
)

$ErrorActionPreference = 'Stop'
if (-not $AuthorsFile) {
  $AuthorsFile = Join-Path $PSScriptRoot 'seed-notes-data.json'
}
if (-not $DataFile) {
  $DataFile = Join-Path $PSScriptRoot 'seed-demo-test-data.json'
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
  $tmpIn = Join-Path $env:TEMP ("ff-demo-in-{0}.json" -f [guid]::NewGuid().ToString('N'))
  $tmpOut = Join-Path $env:TEMP ("ff-demo-out-{0}.json" -f [guid]::NewGuid().ToString('N'))
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

function Reset-DemoPasswordViaMysql([string]$Username, [string]$PlainPassword) {
  # BCrypt for "test" (cost 10). For other passwords, generate with python bcrypt.
  if ($PlainPassword -ne 'test') {
    throw 'Reset-DemoPasswordViaMysql only supports password=test in this script'
  }
  $hash = '$2a$10$2mwn5ln.Fhwq0crvbMGNsedGHinJa5TyKfD3lyo0UyFqICxuGZImC'
  $pw = $env:MYSQL_PASSWORD
  if (-not $pw) { throw 'MYSQL_PASSWORD env required to reset demo password' }
  $sql = "UPDATE user.user SET password='$hash' WHERE username='$Username';"
  $null = docker exec $MysqlContainer mysql -uroot "-p$pw" -e $sql 2>$null
  Write-Host ("reset password for @{0} via mysql" -f $Username)
}

function Ensure-User([string]$Username, [string]$Password, [string]$Nickname, [switch]$AllowMysqlReset) {
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

  # username exists but wrong password (common for demo account)
  if ($AllowMysqlReset -and ("{0}" -f $reg.message) -match '已存在|exist') {
    Reset-DemoPasswordViaMysql -Username $Username -PlainPassword $Password
    Start-Sleep -Milliseconds 400
    $loginReset = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/user/login" -Body @{
      username          = $Username
      password          = $Password
      deviceFingerprint = $DeviceFingerprint
    }
    if ($loginReset.code -eq 0 -and ($loginReset.data.accessToken -or $loginReset.data.token)) {
      return $loginReset.data
    }
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

function Get-Token($auth) {
  $t = $auth.accessToken
  if (-not $t) { $t = $auth.token }
  if (-not $t) { throw 'no access token' }
  return [string]$t
}

function Get-LocalCoverPool {
  $dir = Join-Path $PSScriptRoot 'seed-covers'
  if (-not (Test-Path -LiteralPath $dir)) { return @() }
  return @(Get-ChildItem -LiteralPath $dir -Filter '*.jpg' -File | Sort-Object Name)
}

function Upload-Cover([string]$Token, [string]$JpegPath) {
  $tmpOut = Join-Path $env:TEMP ("ff-demo-up-{0}.json" -f [guid]::NewGuid().ToString('N'))
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

Write-Host ("Gateway: {0}" -f $GatewayBase)
Write-Host ("Demo account: {0} / {1}" -f $DemoUser, $DemoPassword)

if (-not (Test-Path -LiteralPath $AuthorsFile)) {
  throw "authors file not found: $AuthorsFile (run seed-notes.ps1 first)"
}
if (-not (Test-Path -LiteralPath $DataFile)) {
  throw "data file not found: $DataFile"
}

$authorsData = Get-Content -LiteralPath $AuthorsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$demoData = Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json
$authors = @($authorsData.authors)

$seedSessions = @()
foreach ($a in $authors) {
  try {
    $auth = Ensure-User -Username $a.u -Password $SeedPassword -Nickname $a.n
    $seedSessions += @{
      u     = $a.u
      n     = $a.n
      token = (Get-Token $auth)
      id    = [long]$auth.user.id
    }
    Write-Host ("seed ok @{0} id={1}" -f $a.u, $auth.user.id)
  } catch {
    Write-Warning ("seed user {0} unavailable: {1}" -f $a.u, $_)
  }
}
if ($seedSessions.Count -lt 1) {
  throw 'no seed authors available; run seed-notes.ps1 first'
}

$demoAuth = Ensure-User -Username $DemoUser -Password $DemoPassword -Nickname ([string]$demoData.nickname) -AllowMysqlReset
$demoToken = Get-Token $demoAuth
$demoId = [long]$demoAuth.user.id
Write-Host ("demo user id={0}" -f $demoId)

$upd = Invoke-JsonApi -Method PUT -Url "$GatewayBase/api/user/me" `
  -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) } `
  -Body @{
    nickname  = [string]$demoData.nickname
    bio       = [string]$demoData.bio
    avatarUrl = [string]$demoData.avatarUrl
  }
if ($upd.code -eq 0) {
  Write-Host 'profile updated'
} else {
  Write-Warning ("profile update failed: {0}" -f $upd.message)
}

$followOk = 0
foreach ($s in $seedSessions) {
  try {
    $res = Invoke-JsonApi -Method POST -Url ("{0}/api/user/follow/{1}" -f $GatewayBase, $s.id) `
      -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) }
    if ($res.code -eq 0) { $followOk++ }
  } catch {
    Write-Warning ("follow {0} failed: {1}" -f $s.u, $_)
  }
}
Write-Host ("followed {0}/{1} seed authors" -f $followOk, $seedSessions.Count)

$demoNoteIds = @()
if (-not $SkipNotes.IsPresent) {
  $pool = Get-LocalCoverPool
  $demoNotes = @($demoData.notes)
  $i = 0
  foreach ($note in $demoNotes) {
    $i++
    $coverUrl = $null
    if ($pool.Count -gt 0) {
      $jpg = $pool[($i - 1) % $pool.Count].FullName
      $coverUrl = Upload-Cover -Token $demoToken -JpegPath $jpg
    }
    $body = @{
      title   = [string]$note.t
      content = [string]$note.c
    }
    if ($coverUrl) {
      $body.coverUrl = $coverUrl
      $body.mediaUrls = @($coverUrl)
    }
    try {
      $res = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/note" `
        -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) } `
        -IdempotencyKey ("demo-test-note-{0}" -f $i) `
        -Body $body
      if ($res.code -eq 0) {
        $nid = $res.data.id
        if (-not $nid -and $res.data.note) { $nid = $res.data.note.id }
        if ($nid) { $demoNoteIds += [long]$nid }
        Write-Host ("note OK #{0} id={1} {2}" -f $i, $nid, $note.t)
      } else {
        Write-Warning ("note FAIL #{0}: {1}" -f $i, $res.message)
      }
    } catch {
      Write-Warning ("note ERR #{0}: {1}" -f $i, $_)
    }
  }
}

if ($ForceApproveViaMysql.IsPresent -and $demoNoteIds.Count -gt 0) {
  $ids = ($demoNoteIds -join ',')
  $sql = "UPDATE content.note SET status=1 WHERE id IN ($ids) AND status=2;"
  Write-Host ("force approve via mysql: {0}" -f $sql)
  try {
    $pw = $env:MYSQL_PASSWORD
    if (-not $pw) {
      Write-Warning 'MYSQL_PASSWORD env empty; skip force approve'
    } else {
      $null = docker exec $MysqlContainer mysql -uroot "-p$pw" -e $sql 2>$null
      Write-Host 'mysql approve done'
    }
  } catch {
    Write-Warning ("mysql approve failed: {0}" -f $_)
  }
}

if (-not $SkipLikes.IsPresent) {
  $likeCount = 0
  $collectCount = 0
  foreach ($s in ($seedSessions | Select-Object -First 4)) {
    try {
      $list = Invoke-JsonApi -Method GET -Url ("{0}/api/note/user/{1}?page=1&size=5" -f $GatewayBase, $s.id) `
        -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) }
      $notes = @()
      if ($list.code -eq 0) { $notes = @($list.data) }
      foreach ($n in ($notes | Select-Object -First 2)) {
        $nid = [long]$n.id
        $lr = Invoke-JsonApi -Method POST -Url ("{0}/api/social/like/{1}" -f $GatewayBase, $nid) `
          -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) }
        if ($lr.code -eq 0) { $likeCount++ }
        $cr = Invoke-JsonApi -Method POST -Url ("{0}/api/social/collect/{1}" -f $GatewayBase, $nid) `
          -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) }
        if ($cr.code -eq 0) { $collectCount++ }
      }
    } catch {
      Write-Warning ("like/collect from @{0} failed: {1}" -f $s.u, $_)
    }
  }
  Write-Host ("liked={0} collected={1}" -f $likeCount, $collectCount)
}

if (-not $SkipMarket.IsPresent) {
  try {
    $addrs = Invoke-JsonApi -Method GET -Url "$GatewayBase/api/market/addresses" `
      -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) }
    $has = $false
    if ($addrs.code -eq 0 -and $addrs.data -and @($addrs.data).Count -gt 0) { $has = $true }
    if (-not $has) {
      $addr = $demoData.address
      $ar = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/market/addresses" `
        -Headers @{ Authorization = ("Bearer {0}" -f $demoToken) } `
        -Body @{
          receiverName = [string]$addr.receiverName
          phone        = [string]$addr.phone
          province     = [string]$addr.province
          city         = [string]$addr.city
          district     = [string]$addr.district
          detail       = [string]$addr.detail
          isDefault    = [bool]$addr.isDefault
        }
      if ($ar.code -eq 0) {
        Write-Host 'market address created'
      } else {
        Write-Warning ("address failed: {0}" -f $ar.message)
      }
    } else {
      Write-Host 'market address already exists'
    }
  } catch {
    Write-Warning ("market address skipped: {0}" -f $_)
  }
}

$imScript = Join-Path $PSScriptRoot 'seed-im.ps1'
if (Test-Path -LiteralPath $imScript) {
  try {
    Write-Host '==> Seed IM (optional)' -ForegroundColor Cyan
    & $imScript -GatewayBase $GatewayBase -DemoUser $DemoUser -DemoPassword $DemoPassword
  } catch {
    Write-Warning ("seed-im skipped: {0}" -f $_)
  }
}

Write-Host ''
Write-Host 'Done. Demo login: test / test'
Write-Host 'Checklist: discover, following feed, my notes, likes/collects, market address, messages/DM'
