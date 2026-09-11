# 本机线上同路径 NSFW 压测：本地 HTTP /check（GPU），无 Docker
# 对齐 moderation-service：POST multipart → JSON
# 用法（仓库根目录）:
#   powershell -File bench/scripts/bench-nsfw-local-online.ps1
param(
    [string]$ImagePath = "",
    [int]$N = 100,
    [int]$Warmup = 10,
    [string]$Concurrency = "1,2",
    [int]$Port = 13333,
    [string]$Device = "cuda",
    [string]$CondaEnv = "pytorch"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not $ImagePath) {
    $candidates = @(
        (Join-Path $Root "web-console\tmp-covers\cat.jpg"),
        (Join-Path $Root "web-console\src\assets\hero.png")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $ImagePath = $c; break }
    }
}
if (-not $ImagePath -or -not (Test-Path $ImagePath)) {
    throw "测试图不存在，请传 -ImagePath"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutDir = Join-Path $Root "bench\reports"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$report = Join-Path $OutDir "nsfw-local-online-$stamp.md"
$rawJson = Join-Path $OutDir "nsfw-local-online-$stamp.json"
$scriptPy = Join-Path $Root "bench\scripts\bench_nsfw_local_online.py"

$conda = "D:\DeepLearning\Anaconda\Scripts\conda.exe"
if (-not (Test-Path $conda)) { throw "找不到 conda: $conda" }

Write-Host "本机线上同路径压测 device=$Device port=$Port ..." -ForegroundColor Cyan
$env:HF_ENDPOINT = "https://hf-mirror.com"
$env:HUGGINGFACE_HUB_CACHE = Join-Path $env:USERPROFILE ".cache\huggingface\hub"

& $conda run -n $CondaEnv --no-capture-output python $scriptPy `
    --image $ImagePath --n $N --warmup $Warmup --concurrency $Concurrency `
    --device $Device --port $Port --out-json $rawJson
if ($LASTEXITCODE -ne 0) { throw "压测失败 exit=$LASTEXITCODE" }
if (-not (Test-Path $rawJson)) { throw "未生成结果 JSON: $rawJson" }
$obj = Get-Content -Raw -Encoding utf8 $rawJson | ConvertFrom-Json

$hostCpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$rows = @()
foreach ($prop in $obj.by_concurrency.PSObject.Properties) {
    $s = $prop.Value
    $rows += "| $($s.concurrency) | $($s.ok) | $($s.fail) | $($s.qps) | $($s.min_ms) | $($s.avg_ms) | $($s.p50_ms) | $($s.p90_ms) | $($s.p95_ms) | **$($s.p99_ms)** | $($s.max_ms) |"
}

$ip = $obj.inprocess_e2e
$md = @"
# NSFW local online path (HTTP /check, no Docker)

* time: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
* model: ``$($obj.model)``
* device: **$($obj.device)**
* gpu: $($obj.gpu)
* host_cpu: $hostCpu
* conda_env: ``$CondaEnv``
* image: ``$(Split-Path $ImagePath -Leaf)`` ($([math]::Round($obj.image_bytes/1KB,1)) KB)
* batch_size: $($obj.batch_size)
* input_size: $($obj.input_size)
* TensorRT/ONNX: **$($obj.tensorrt_onnx)**
* params: N=$N Warmup=$Warmup Concurrency=$Concurrency
* note: 本机进程提供 ``/check``，路径对齐 moderation（multipart 上传 + 预处理 + 推理 + JSON），**无 Docker**

## HTTP e2e

| c | ok | fail | QPS | min | avg | P50 | P90 | P95 | **P99** | max |
|---|---:|-----:|----:|----:|----:|----:|----:|----:|--------:|----:|
$($rows -join "`n")

## In-process e2e（同模型，无 HTTP；含 decode+preprocess+forward）

| metric | ms |
|--------|---:|
| P50 | $($ip.p50_ms) |
| P95 | $($ip.p95_ms) |
| **P99** | **$($ip.p99_ms)** |
| avg | $($ip.avg_ms) |

* model_load_ms: $($obj.model_load_ms)
* Raw: ``$(Split-Path $rawJson -Leaf)``
"@
Set-Content -Encoding utf8 -Path $report -Value $md
Write-Host ""
Write-Host "report: $report" -ForegroundColor Green
$c1 = $obj.by_concurrency.'1'
if ($c1) {
    Write-Host ("RESUME_HTTP_P99_MS={0} INPROC_P99_MS={1} GPU={2}" -f $c1.p99_ms, $ip.p99_ms, $obj.gpu) -ForegroundColor Yellow
}
