#Requires -Version 5.1
<#
.SYNOPSIS
  Seed a demo DM between test and a seed author (Outbox -> notify IM).

.EXAMPLE
  .\deploy\scripts\seed-im.ps1
#>
param(
  [string]$GatewayBase = 'http://127.0.0.1:8080',
  [string]$DemoUser = 'test',
  [string]$DemoPassword = 'test',
  [string]$PeerUser = 'seed_momo',
  [string]$PeerPassword = '123456',
  [string]$DeviceFingerprint = 'seed-im-device-01',
  [string]$DataFile = ''
)

$ErrorActionPreference = 'Stop'
if (-not $DataFile) {
  $DataFile = Join-Path $PSScriptRoot 'seed-im-data.json'
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
  $tmpIn = Join-Path $env:TEMP ("ff-im-in-{0}.json" -f [guid]::NewGuid().ToString('N'))
  $tmpOut = Join-Path $env:TEMP ("ff-im-out-{0}.json" -f [guid]::NewGuid().ToString('N'))
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

function Login([string]$Username, [string]$Password) {
  $res = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/user/login" -Body @{
    username = $Username
    password = $Password
    deviceFingerprint = $DeviceFingerprint
  }
  if ($res.code -ne 0) { throw "login failed for ${Username}: $($res.message)" }
  $token = $res.data.accessToken
  if (-not $token) { $token = $res.data.token }
  if (-not $token) { throw "no token for $Username" }
  return @{
    token = $token
    userId = [long]$res.data.user.id
    username = $Username
  }
}

$data = Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json

Write-Host "==> Login $DemoUser / $PeerUser" -ForegroundColor Cyan
$demo = Login $DemoUser $DemoPassword
$peer = Login $PeerUser $PeerPassword
$demoH = @{ Authorization = "Bearer $($demo.token)" }
$peerH = @{ Authorization = "Bearer $($peer.token)" }

Write-Host "==> Open DM $($demo.userId) <-> $($peer.userId)" -ForegroundColor Cyan
$open = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/im/conversations/open" -Headers $demoH -Body @{
  peerUserId = $peer.userId
}
if ($open.code -ne 0) { throw "open conversation failed: $($open.message)" }
$convId = [long]$open.data.id
Write-Host "  conversationId=$convId"

foreach ($m in $data.messages) {
  $h = if ($m.from -eq 'demo') { $demoH } else { $peerH }
  $who = if ($m.from -eq 'demo') { $DemoUser } else { $PeerUser }
  $idem = "seed-im-{0}-{1}" -f $convId, ([guid]::NewGuid().ToString('N').Substring(0, 8))
  $sent = Invoke-JsonApi -Method POST -Url "$GatewayBase/api/im/conversations/$convId/messages" `
    -Headers $h -Body @{ content = [string]$m.text } -IdempotencyKey $idem
  if ($sent.code -ne 0) { throw "send failed ($who): $($sent.message)" }
  Write-Host ("  [{0}] {1}" -f $who, $m.text)
  Start-Sleep -Milliseconds 400
}

Write-Host ''
Write-Host 'Seed IM done. Login as test/test -> Messages -> DM tab' -ForegroundColor Green
