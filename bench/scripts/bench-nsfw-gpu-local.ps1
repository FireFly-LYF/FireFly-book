# 本机 GPU NSFW 纯推理压测（不用 Docker）
# 依赖：conda env `pytorch`（已含 torch+cu）
# 用法（仓库根目录）:
#   powershell -File bench/scripts/bench-nsfw-gpu-local.ps1
param(
    [string]$ImagePath = "",
    [int]$N = 100,
    [int]$Warmup = 20,
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

$conda = "D:\DeepLearning\Anaconda\Scripts\conda.exe"
if (-not (Test-Path $conda)) { throw "找不到 conda: $conda" }

Write-Host "检查/安装 transformers（清华源）..." -ForegroundColor Cyan
& $conda run -n $CondaEnv python -c "import transformers" 2>$null
if ($LASTEXITCODE -ne 0) {
    & $conda run -n $CondaEnv pip install -q "transformers==4.49.0" pillow accelerate `
        -i https://pypi.tuna.tsinghua.edu.cn/simple
    if ($LASTEXITCODE -ne 0) { throw "pip install transformers 失败" }
}

Write-Host "开始本机 GPU 推理压测 device=$Device ..." -ForegroundColor Cyan
$env:HF_ENDPOINT = "https://hf-mirror.com"
$env:HUGGINGFACE_HUB_CACHE = Join-Path $env:USERPROFILE ".cache\huggingface\hub"

$out = & $conda run -n $CondaEnv --no-capture-output python $scriptPy `
    --image $ImagePath --n $N --warmup $Warmup --device $Device 2>&1
$out | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { throw "压测失败 exit=$LASTEXITCODE" }

$text = ($out | Out-String)
$start = $text.LastIndexOf("{")
$end = $text.LastIndexOf("}")
if ($start -lt 0 -or $end -lt $start) { throw "未解析到 JSON 结果" }
$json = $text.Substring($start, $end - $start + 1)
$json | Set-Content -Encoding utf8 $rawJson
$obj = $json | ConvertFrom-Json

$hostCpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
$md = @"
# NSFW Falconsai local GPU pure inference

* time: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
* model: ``$($obj.model)``
* device: **$($obj.device)**
* gpu: $($obj.gpu)
* host_cpu: $hostCpu
* conda_env: ``$CondaEnv``
* image: ``$(Split-Path $ImagePath -Leaf)``
* params: N=$N Warmup=$Warmup
* mirrors: hf-mirror.com (weights) / tuna PyPI (pip if needed)
* note: **in-process forward only** (no Docker / no HTTP)

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
Write-Host ("RESUME_P99_MS={0} DEVICE={1}" -f $obj.p99_ms, $obj.device) -ForegroundColor Yellow
