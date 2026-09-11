# 本地 GPU NSFW 纯推理压测（不走 Docker）
# 依赖：conda env `pytorch`（已含 torch+cu / transformers / pillow）
# 用法（仓库根目录）:
#   powershell -File bench/scripts/bench-nsfw-gpu.ps1
#   powershell -File bench/scripts/bench-nsfw-gpu.ps1 -N 100 -Warmup 20 -Device cuda
param(
    [string]$ImagePath = "",
    [int]$N = 100,
    [int]$Warmup = 20,
    [ValidateSet("auto", "cuda", "cpu")]
    [string]$Device = "cuda",
    [string]$CondaEnv = "pytorch"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not $ImagePath) {
    $ImagePath = Join-Path $Root "web-console\tmp-covers\cat.jpg"
}
if (-not (Test-Path $ImagePath)) { throw "测试图不存在: $ImagePath" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutDir = Join-Path $Root "bench\reports"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$report = Join-Path $OutDir "nsfw-gpu-infer-$stamp.md"
$rawJson = Join-Path $OutDir "nsfw-gpu-infer-$stamp.json"
$scriptPy = Join-Path $Root "bench\scripts\bench_nsfw_gpu_infer.py"

# 换源：HF 权重走 hf-mirror，避免 huggingface.co SSL/超时
$env:HF_ENDPOINT = "https://hf-mirror.com"
$env:HUGGINGFACE_HUB_CACHE = Join-Path $Root "bench\.cache\huggingface"
New-Item -ItemType Directory -Force -Path $env:HUGGINGFACE_HUB_CACHE | Out-Null

Write-Host "conda env=$CondaEnv device=$Device HF_ENDPOINT=$($env:HF_ENDPOINT)" -ForegroundColor Cyan
Write-Host "image=$ImagePath N=$N Warmup=$Warmup" -ForegroundColor Cyan

$out = & conda run -n $CondaEnv --no-capture-output python $scriptPy `
    --image $ImagePath `
    --n $N `
    --warmup $Warmup `
    --device $Device 2>&1
$out | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { throw "本地推理压测失败 (exit=$LASTEXITCODE)" }

$text = ($out | Out-String)
$start = $text.LastIndexOf("{")
$end = $text.LastIndexOf("}")
if ($start -lt 0 -or $end -lt $start) { throw "未解析到 JSON 结果" }
$json = $text.Substring($start, $end - $start + 1)
$obj = $json | ConvertFrom-Json
$json | Set-Content -Encoding utf8 $rawJson

$hostCpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$md = @"
# NSFW Falconsai pure inference (local, no Docker)

* time: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
* model: ``$($obj.model)``
* device: **$($obj.device)**
* gpu: $($obj.gpu)
* host_cpu: $hostCpu
* conda_env: ``$CondaEnv``
* image: ``$(Split-Path $ImagePath -Leaf)``
* params: N=$N Warmup=$Warmup
* mirrors: HF_ENDPOINT=https://hf-mirror.com
* note: **in-process model forward only** (preprocess once; timed loop is forward+sync)

| metric | ms |
|--------|---:|
| model_load | $($obj.model_load_ms) |
| min | $($obj.min_ms) |
| avg | $($obj.avg_ms) |
| P50 | $($obj.p50_ms) |
| P90 | $($obj.p90_ms) |
| P95 | $($obj.p95_ms) |
| **P99** | **$($obj.p99_ms)** |
| max | $($obj.max_ms) |

* QPS ≈ $($obj.qps)
* Raw: ``$(Split-Path $rawJson -Leaf)``
"@

Set-Content -Encoding utf8 -Path $report -Value $md
Write-Host ""
Write-Host "report: $report" -ForegroundColor Green
Write-Host "raw: $rawJson" -ForegroundColor Green
Write-Host ("RESUME_P99_MS={0} DEVICE={1}" -f $obj.p99_ms, $obj.device) -ForegroundColor Yellow
