# FireFly / Gateway 压测：直连 Java vs 经网关
# 依赖：hey（go install github.com/rakyll/hey@latest）
# 用法：在任意目录
#   powershell -File Gateway/gateway/scripts/bench.ps1
#   powershell -File Gateway/gateway/scripts/bench.ps1 -N 10000 -C 100

param(
    [int]$N = 5000,   # 总请求数
    [int]$C = 50,     # 并发
    [string]$UserId = "1"
)

$ErrorActionPreference = "Stop"
$hey = Get-Command hey -ErrorAction SilentlyContinue
if (-not $hey) {
    $candidate = Join-Path $env:USERPROFILE "go\bin\hey.exe"
    if (Test-Path $candidate) {
        $env:PATH = "$(Split-Path $candidate);$env:PATH"
    } else {
        Write-Host "未找到 hey。先执行：go install github.com/rakyll/hey@latest"
        exit 1
    }
}

function Run-Bench([string]$title, [string]$url, [hashtable]$headers = @{}) {
    Write-Host ""
    Write-Host "======== $title ========" -ForegroundColor Cyan
    Write-Host "URL: $url  n=$N c=$C"
    $args = @("-n", "$N", "-c", "$C")
    foreach ($k in $headers.Keys) {
        $args += @("-H", "${k}: $($headers[$k])")
    }
    $args += $url
    & hey @args
}

# 轻量读接口：两边路径一致，可比网关开销
$direct = "http://127.0.0.1:9001/api/user/$UserId"
$viaGw  = "http://127.0.0.1:8080/api/user/$UserId"

Write-Host "压测前请确认：user-service :9001 与 Gateway :8080 已启动；jwt.api_required=false"

Run-Bench "1) 直连 user-service" $direct
Run-Bench "2) 经 Gateway 转发" $viaGw @{ "X-User-Id" = $UserId }

Write-Host ""
Write-Host "看 Summary 里的 Requests/sec 与 Latency。经网关通常更低（多一跳 + 中间件）。" -ForegroundColor Yellow
Write-Host "测限流：把 gateway.yaml 的 ratelimit.rate/capacity 临时改成 20，重启网关再跑第 2 项，应出现大量 Non-2xx/429。"
