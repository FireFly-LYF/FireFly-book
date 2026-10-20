#Requires -Version 5.1
<#
.SYNOPSIS
  Seed comments (incl. nested replies) for every note via Gateway.

.EXAMPLE
  .\deploy\scripts\seed-comments.ps1
  .\deploy\scripts\seed-comments.ps1 -TopPerNote 4 -RepliesPerTop 2
#>
param(
  [string]$GatewayBase = 'http://127.0.0.1:8080',
  [string]$Password = '123456',
  [int]$TopPerNote = 3,
  [int]$RepliesPerTop = 2,
  [string]$DeviceFingerprint = 'seed-notes-device-fingerprint-01',
  [string]$AuthorsFile = '',
  [string]$DataFile = '',
  [switch]$SkipIfHasComments
)

$ErrorActionPreference = 'Stop'
if (-not $AuthorsFile) {
  $AuthorsFile = Join-Path $PSScriptRoot 'seed-notes-data.json'
}
if (-not $DataFile) {
  $DataFile = Join-Path $PSScriptRoot 'seed-comments-data.json'
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
  $tmpIn = Join-Path $env:TEMP ("ff-cseed-in-{0}.json" -f [guid]::NewGuid().ToString('N'))
  $tmpOut = Join-Path $env:TEMP ("ff-cseed-out-{0}.json" -f [guid]::NewGuid().ToString('N'))
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

function Get-UserNotes([string]$Token, [long]$UserId) {
  $all = New-Object System.Collections.Generic.List[object]
  $page = 1
  $size = 50
  while ($true) {
    $res = Invoke-JsonApi -Method GET -Url ("{0}/api/note/user/{1}?page={2}&size={3}" -f $GatewayBase, $UserId, $page, $size) -Headers @{
      Authorization = ("Bearer {0}" -f $Token)
    }
    if ($res.code -ne 0) {
      throw ("list notes failed userId={0}: {1}" -f $UserId, $res.message)
    }
    $batch = @($res.data)
    if ($batch.Count -eq 0) { break }
    foreach ($n in $batch) { $all.Add($n) | Out-Null }
    if ($batch.Count -lt $size) { break }
    $page++
    if ($page -gt 40) { break }
  }
  return $all
}

function New-Comment([string]$Token, [long]$NoteId, [string]$Content, [object]$ParentId) {
  $body = @{
    noteId  = $NoteId
    content = $Content
  }
  if ($null -ne $ParentId) {
    $body.parentId = [long]$ParentId
  }
  return Invoke-JsonApi -Method POST -Url "$GatewayBase/api/social/comment" `
    -Headers @{ Authorization = ("Bearer {0}" -f $Token) } `
    -IdempotencyKey ([guid]::NewGuid().ToString()) `
    -Body $body
}

if (-not (Test-Path -LiteralPath $AuthorsFile)) {
  throw "authors file not found: $AuthorsFile"
}
if (-not (Test-Path -LiteralPath $DataFile)) {
  throw "comments data file not found: $DataFile"
}

$authorsData = [IO.File]::ReadAllText($AuthorsFile, [Text.UTF8Encoding]::new($false)) | ConvertFrom-Json
$commentData = [IO.File]::ReadAllText($DataFile, [Text.UTF8Encoding]::new($false)) | ConvertFrom-Json
$authors = @($authorsData.authors)
$topPool = @($commentData.topLevel)
$replyPool = @($commentData.replies)

Write-Host ("Gateway: {0}" -f $GatewayBase)
Write-Host ("Authors: {0}, top/note: {1}, replies/top: {2}" -f $authors.Count, $TopPerNote, $RepliesPerTop)

$sessions = @()
foreach ($a in $authors) {
  Write-Host ("ensure user {0} ({1}) ..." -f $a.u, $a.n)
  $auth = Ensure-User -Username $a.u -Nickname $a.n
  $token = $auth.accessToken
  if (-not $token) { $token = $auth.token }
  if (-not $token) { throw ("no token for {0}" -f $a.u) }
  $sessions += @{ u = $a.u; n = $a.n; token = $token; id = [long]$auth.user.id }
}

Write-Host 'collect notes from all seed authors...'
$notes = New-Object System.Collections.Generic.List[object]
$seen = @{}
foreach ($s in $sessions) {
  $wall = Get-UserNotes -Token $s.token -UserId $s.id
  foreach ($n in $wall) {
    $nid = [string]$n.id
    if ($seen.ContainsKey($nid)) { continue }
    $seen[$nid] = $true
    $notes.Add($n) | Out-Null
  }
  Write-Host ("  @{0}: {1} notes" -f $s.u, $wall.Count)
}

if ($notes.Count -eq 0) {
  throw 'no notes found; run seed-notes.ps1 first'
}

$rnd = [Random]::new(7)
$okTop = 0
$okReply = 0
$skipNotes = 0
$failCount = 0
$noteIdx = 0

foreach ($note in $notes) {
  $noteIdx++
  $noteId = [long]$note.id
  $viewer = $sessions[$rnd.Next($sessions.Count)]

  if ($SkipIfHasComments.IsPresent) {
    $existing = Invoke-JsonApi -Method GET -Url ("{0}/api/social/comment/{1}?page=1&size=1" -f $GatewayBase, $noteId) -Headers @{
      Authorization = ("Bearer {0}" -f $viewer.token)
    }
    if ($existing.code -eq 0 -and @($existing.data).Count -gt 0) {
      $skipNotes++
      Write-Host ("[{0}/{1}] skip note#{2} (has comments)" -f $noteIdx, $notes.Count, $noteId)
      continue
    }
  }

  $topIds = New-Object System.Collections.Generic.List[long]
  $topCount = $TopPerNote
  if ($topCount -lt 1) { $topCount = 1 }

  for ($t = 0; $t -lt $topCount; $t++) {
    $author = $sessions[$rnd.Next($sessions.Count)]
    $text = [string]$topPool[$rnd.Next($topPool.Count)]
    try {
      $res = New-Comment -Token $author.token -NoteId $noteId -Content $text -ParentId $null
      if ($res.code -eq 0 -and $res.data.id) {
        $okTop++
        $topIds.Add([long]$res.data.id) | Out-Null
      } else {
        $failCount++
        Write-Warning ("note#{0} top FAIL @{1}: {2}" -f $noteId, $author.u, $res.message)
      }
    } catch {
      $failCount++
      Write-Warning ("note#{0} top ERR @{1}: {2}" -f $noteId, $author.u, $_)
    }
  }

  for ($ri = 0; $ri -lt $topIds.Count; $ri++) {
    $rootId = $topIds[$ri]
    $replyN = $RepliesPerTop
    if ($replyN -lt 0) { $replyN = 0 }
    # vary a bit: some tops get one more reply
    if ($rnd.Next(3) -eq 0) { $replyN++ }

    $lastReplyId = $rootId
    for ($r = 0; $r -lt $replyN; $r++) {
      $author = $sessions[$rnd.Next($sessions.Count)]
      $text = [string]$replyPool[$rnd.Next($replyPool.Count)]
      # half reply to root, half to previous reply (still 2-level, sets replyToUserId)
      $parent = if (($r -gt 0) -and ($rnd.Next(2) -eq 0)) { $lastReplyId } else { $rootId }
      try {
        $res = New-Comment -Token $author.token -NoteId $noteId -Content $text -ParentId $parent
        if ($res.code -eq 0 -and $res.data.id) {
          $okReply++
          $lastReplyId = [long]$res.data.id
        } else {
          $failCount++
          Write-Warning ("note#{0} reply FAIL @{1}: {2}" -f $noteId, $author.u, $res.message)
        }
      } catch {
        $failCount++
        Write-Warning ("note#{0} reply ERR @{1}: {2}" -f $noteId, $author.u, $_)
      }
    }
  }

  Write-Host ("[{0}/{1}] note#{2} tops={3} (running top={4} reply={5})" -f `
    $noteIdx, $notes.Count, $noteId, $topIds.Count, $okTop, $okReply)
}

Write-Host ''
Write-Host ("Done. notes={0} skipped={1} topOk={2} replyOk={3} fail={4}" -f `
  $notes.Count, $skipNotes, $okTop, $okReply, $failCount)
Write-Host ("Password for all seed users: {0}" -f $Password)
Write-Host 'Accounts: seed_momo / seed_yoyo / seed_kira / seed_bean / seed_lin / seed_fit / seed_home / seed_skincare'
