# NSFW detector 单图推理延迟压测（仅 curl.exe，无 Python 依赖）
# 用法（仓库根目录）:
#   powershell -File bench/scripts/bench-nsfw-detector.ps1
#   powershell -File bench/scripts/bench-nsfw-detector.ps1 -N 100 -Warmup 10 -Concurrency 1,2,4
#
# 前置：docker start nsfw-detector（或 compose 起 ff-nsfw-detector），监听 :3333

param(
    [string]$Url = "http://127.0.0.1:3333/check",
    [string]$Image = "",
    [int]$N = 100,
    [int]$Warmup = 10,
    [string]$Concurrency = "1,2,4",
    [string]$OutDir = ""
)
$ConcurrencyLevels = @(
    $Concurrency.Split(@(',', ' ', ';'), [System.StringSplitOptions]::RemoveEmptyEntries) |
    ForEach-Object { [int]$_.Trim() } |
    Where-Object { $_ -gt 0 }
)
if (-not $ConcurrencyLevels -or $ConcurrencyLevels.Count -eq 0) {
    $ConcurrencyLevels = @(1)
}

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not $Image) {
    $Image = Join-Path $Root "web-console\tmp-covers\cat.jpg"
}
if (-not (Test-Path $Image)) {
    throw "测试图不存在: $Image"
}
if (-not $OutDir) {
    $OutDir = Join-Path $Root "bench\reports"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$report = Join-Path $OutDir "nsfw-detector-$stamp.md"
$rawCsv = Join-Path $OutDir "nsfw-detector-$stamp.raw.csv"

function Get-Percentile([double[]]$sorted, [double]$p) {
    if ($sorted.Count -eq 0) { return [double]::NaN }
    if ($sorted.Count -eq 1) { return $sorted[0] }
    $rank = ($p / 100.0) * ($sorted.Count - 1)
    $lo = [Math]::Floor($rank)
    $hi = [Math]::Ceiling($rank)
    if ($lo -eq $hi) { return $sorted[$lo] }
    $w = $rank - $lo
    return $sorted[$lo] * (1 - $w) + $sorted[$hi] * $w
}

function Invoke-CheckOnce([string]$endpoint, [string]$imgPath) {
    $tmpBody = [System.IO.Path]::GetTempFileName()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & curl.exe -s -S -m 120 -o $tmpBody -w "%{http_code}" -X POST -F "file=@$imgPath" $endpoint | Out-Null
    $code = $LASTEXITCODE
    $sw.Stop()
    $http = "000"
    $body = ""
    if (Test-Path $tmpBody) {
        # curl -w 写到 stdout，上面用 Out-Null 丢了；改用临时文件拿 body，再用单独测时
        $body = Get-Content -Raw -Path $tmpBody -ErrorAction SilentlyContinue
        Remove-Item -Force $tmpBody -ErrorAction SilentlyContinue
    }
    return [pscustomobject]@{
        Ok      = ($code -eq 0)
        Ms      = [Math]::Round($sw.Elapsed.TotalMilliseconds, 3)
        Body    = $body
        CurlExit = $code
    }
}

# 更可靠：curl 把 http_code 写到 stderr 旁路文件
function Invoke-CheckTimed([string]$endpoint, [string]$imgPath) {
    $bodyFile = [System.IO.Path]::GetTempFileName()
    $codeFile = [System.IO.Path]::GetTempFileName()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $httpCode = & curl.exe -s -S -m 120 -o $bodyFile -w "%{http_code}" -X POST -F "file=@$imgPath" $endpoint 2>$null
    $sw.Stop()
    $curlExit = $LASTEXITCODE
    $body = ""
    if (Test-Path $bodyFile) {
        $body = Get-Content -Raw -Path $bodyFile -ErrorAction SilentlyContinue
        Remove-Item -Force $bodyFile -ErrorAction SilentlyContinue
    }
    Remove-Item -Force $codeFile -ErrorAction SilentlyContinue
    $ok = ($curlExit -eq 0 -and $httpCode -eq "200")
    return [pscustomobject]@{
        Ok       = $ok
        HttpCode = "$httpCode"
        Ms       = [Math]::Round($sw.Elapsed.TotalMilliseconds, 3)
        Body     = $body
        CurlExit = $curlExit
    }
}

function Summarize([double[]]$msList, [int]$okCount, [int]$failCount, [double]$wallSec) {
    $sorted = $msList | Sort-Object
    $arr = [double[]]@($sorted)
    $avg = if ($arr.Count -gt 0) { ($arr | Measure-Object -Average).Average } else { [double]::NaN }
    $qps = if ($wallSec -gt 0) { $okCount / $wallSec } else { [double]::NaN }
    return [ordered]@{
        n        = $arr.Count
        ok       = $okCount
        fail     = $failCount
        wall_s   = [Math]::Round($wallSec, 3)
        qps      = [Math]::Round($qps, 2)
        min_ms   = if ($arr.Count) { [Math]::Round($arr[0], 1) } else { $null }
        avg_ms   = if ($arr.Count) { [Math]::Round($avg, 1) } else { $null }
        p50_ms   = if ($arr.Count) { [Math]::Round((Get-Percentile $arr 50), 1) } else { $null }
        p90_ms   = if ($arr.Count) { [Math]::Round((Get-Percentile $arr 90), 1) } else { $null }
        p95_ms   = if ($arr.Count) { [Math]::Round((Get-Percentile $arr 95), 1) } else { $null }
        p99_ms   = if ($arr.Count) { [Math]::Round((Get-Percentile $arr 99), 1) } else { $null }
        max_ms   = if ($arr.Count) { [Math]::Round($arr[-1], 1) } else { $null }
    }
}

Write-Host "探测 $Url ..." -ForegroundColor Cyan
$probe = curl.exe -s -S -m 5 -o NUL -w "%{http_code}" $Url.Replace("/check", "/") 2>$null
if ($LASTEXITCODE -ne 0 -and "$probe" -ne "200" -and "$probe" -ne "405" -and "$probe" -ne "404") {
    # 根路径可能返回 200 HTML；只要通就行
    $probe2 = Invoke-CheckTimed $Url $Image
    if (-not $probe2.Ok) {
        throw "NSFW detector 不可达 ($Url)。请先: docker start nsfw-detector"
    }
}

$imgInfo = Get-Item $Image
Write-Host "预热 Warmup=$Warmup ..." -ForegroundColor Cyan
$cold = $null
for ($i = 0; $i -lt $Warmup; $i++) {
    $r = Invoke-CheckTimed $Url $Image
    if ($i -eq 0) { $cold = $r }
    Write-Host ("  warmup[{0}] {1}ms http={2} ok={3}" -f $i, $r.Ms, $r.HttpCode, $r.Ok)
    if (-not $r.Ok -and $i -eq 0) {
        throw "预热失败: curlExit=$($r.CurlExit) http=$($r.HttpCode) body=$($r.Body)"
    }
}

$csvLines = New-Object System.Collections.Generic.List[string]
$csvLines.Add("phase,concurrency,seq,ok,http_code,ms") | Out-Null
$summaries = @()

foreach ($c in $ConcurrencyLevels) {
    Write-Host ""
    Write-Host "======== concurrency=$c  requests=$N ========" -ForegroundColor Cyan
    $msOk = New-Object System.Collections.Generic.List[double]
    $ok = 0
    $fail = 0
    $wall = [System.Diagnostics.Stopwatch]::StartNew()

    if ($c -le 1) {
        for ($i = 0; $i -lt $N; $i++) {
            $r = Invoke-CheckTimed $Url $Image
            $csvLines.Add(("seq,{0},{1},{2},{3},{4}" -f $c, $i, [int]$r.Ok, $r.HttpCode, $r.Ms)) | Out-Null
            if ($r.Ok) {
                $msOk.Add([double]$r.Ms) | Out-Null
                $ok++
            } else {
                $fail++
            }
            if (($i + 1) % 10 -eq 0) {
                Write-Host ("  progress {0}/{1}" -f ($i + 1), $N)
            }
        }
    } else {
        $script = {
            param($endpoint, $imgPath, $seq)
            $bodyFile = [System.IO.Path]::GetTempFileName()
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $httpCode = & curl.exe -s -S -m 120 -o $bodyFile -w "%{http_code}" -X POST -F "file=@$imgPath" $endpoint 2>$null
            $sw.Stop()
            $curlExit = $LASTEXITCODE
            Remove-Item -Force $bodyFile -ErrorAction SilentlyContinue
            [pscustomobject]@{
                Seq = $seq
                Ok = ($curlExit -eq 0 -and "$httpCode" -eq "200")
                HttpCode = "$httpCode"
                Ms = [Math]::Round($sw.Elapsed.TotalMilliseconds, 3)
            }
        }
        $pool = [runspacefactory]::CreateRunspacePool(1, $c)
        $pool.Open()
        $handles = @()
        for ($i = 0; $i -lt $N; $i++) {
            $ps = [powershell]::Create().AddScript($script).AddArgument($Url).AddArgument($Image).AddArgument($i)
            $ps.RunspacePool = $pool
            $handles += [pscustomobject]@{ Pipe = $ps; Handle = $ps.BeginInvoke() }
        }
        foreach ($h in $handles) {
            $r = $h.Pipe.EndInvoke($h.Handle)[0]
            $h.Pipe.Dispose()
            $csvLines.Add(("conc,{0},{1},{2},{3},{4}" -f $c, $r.Seq, [int]$r.Ok, $r.HttpCode, $r.Ms)) | Out-Null
            if ($r.Ok) {
                $msOk.Add([double]$r.Ms) | Out-Null
                $ok++
            } else {
                $fail++
            }
        }
        $pool.Close()
        $pool.Dispose()
    }

    $wall.Stop()
    $sum = Summarize @($msOk.ToArray()) $ok $fail $wall.Elapsed.TotalSeconds
    $sum["concurrency"] = $c
    $summaries += $sum
    Write-Host ("  ok={0} fail={1} qps={2} p50={3}ms p95={4}ms p99={5}ms max={6}ms" -f `
        $sum.ok, $sum.fail, $sum.qps, $sum.p50_ms, $sum.p95_ms, $sum.p99_ms, $sum.max_ms)
}

# 多图尺寸补充（串行各 20 次）
$sizeRows = @()
$extraImages = @(
    (Join-Path $Root "Gateway\admin\src\assets\hero.png"),
    (Join-Path $Root "web-console\tmp-covers\citywalk.jpg"),
    (Join-Path $Root "assets\firefly-book-sample.png")
) | Where-Object { Test-Path $_ }

Write-Host ""
Write-Host "======== 多图尺寸串行各 20 次 ========" -ForegroundColor Cyan
foreach ($img in $extraImages) {
    $info = Get-Item $img
    $list = New-Object System.Collections.Generic.List[double]
    $ok = 0; $fail = 0
    $wall = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt 20; $i++) {
        $r = Invoke-CheckTimed $Url $info.FullName
        if ($r.Ok) { $list.Add([double]$r.Ms) | Out-Null; $ok++ } else { $fail++ }
    }
    $wall.Stop()
    $sum = Summarize @($list.ToArray()) $ok $fail $wall.Elapsed.TotalSeconds
    $sum["file"] = $info.Name
    $sum["bytes"] = $info.Length
    $sizeRows += $sum
    Write-Host ("  {0} ({1} KB): p50={2}ms p99={3}ms" -f $info.Name, [Math]::Round($info.Length/1KB,1), $sum.p50_ms, $sum.p99_ms)
}

$csvLines | Set-Content -Encoding utf8 $rawCsv

$hostInfo = docker info --format "{{.Name}} / {{.NCPU}}CPU / {{.MemTotal}}" 2>$null
$cpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$memGB = [Math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)

$concText = ($ConcurrencyLevels -join ",")
$imgKb = [Math]::Round($imgInfo.Length / 1KB, 1)
$nowText = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$rawLeaf = Split-Path $rawCsv -Leaf
$coldLine = ""
if ($cold) {
    $coldLine = "* cold_start_first_request_ms: **$($cold.Ms)** (http=$($cold.HttpCode))"
}

$mainRows = New-Object System.Collections.Generic.List[string]
foreach ($s in $summaries) {
    $mainRows.Add(("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | **{9}** | {10} |" -f `
        $s.concurrency, $s.ok, $s.fail, $s.qps, $s.min_ms, $s.avg_ms, $s.p50_ms, $s.p90_ms, $s.p95_ms, $s.p99_ms, $s.max_ms)) | Out-Null
}
$sizeMd = New-Object System.Collections.Generic.List[string]
foreach ($s in $sizeRows) {
    $sizeMd.Add(("| {0} | {1} KB | {2} | {3} | {4} | {5} |" -f `
        $s.file, [Math]::Round($s.bytes / 1KB, 1), $s.p50_ms, $s.p95_ms, $s.p99_ms, $s.avg_ms)) | Out-Null
}

$reportBody = @"
# NSFW detector single-image latency

* time: $nowText
* endpoint: ``$Url``
* image: ``$($imgInfo.Name)`` ($imgKb KB)
* tool: curl.exe (no Python deps)
* params: N=$N Warmup=$Warmup Concurrency=$concText
* host: $cpu / ${memGB}Gi RAM
* docker: $hostInfo
$coldLine

## Main (same image)

| c | ok | fail | QPS | min | avg | P50 | P90 | P95 | **P99** | max |
|---|---:|-----:|----:|----:|----:|----:|----:|----:|--------:|----:|
$($mainRows -join "`n")

## Multi size (20 sequential each)

| file | size | P50 | P95 | P99 | avg |
|------|-----:|----:|----:|----:|----:|
$($sizeMd -join "`n")

## Notes

* Latency includes HTTP upload + preprocess + inference + response (not pure kernel time).
* Image: ``vxlink/nsfw_detector`` (ViT CPU, ~2GB RAM claimed).
* Raw: ``$rawLeaf``
"@

Set-Content -Encoding utf8 -Path $report -Value $reportBody
Write-Host ""
Write-Host "report: $report" -ForegroundColor Green
Write-Host "raw: $rawCsv" -ForegroundColor Green
