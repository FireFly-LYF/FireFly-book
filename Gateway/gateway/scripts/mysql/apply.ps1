# 将 init.sql 应用到 Docker MySQL 容器（可反复执行）
# 用法：在 Gateway 目录下执行  .\scripts\mysql\apply.ps1
# 可选参数：-Container mysql -Password 123456

param(
    [string]$Container = "mysql",
    [string]$Password = "123456"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlFile = Join-Path $scriptDir "init.sql"

if (-not (Test-Path $sqlFile)) {
    throw "找不到 init.sql: $sqlFile"
}

function Test-ContainerRunning([string]$Name) {
    $names = docker ps --filter "name=$Name" --format "{{.Names}}" 2>$null
    return ($names -split "`r?`n" | Where-Object { $_ -eq $Name }).Count -gt 0
}

if (-not (Test-ContainerRunning $Container)) {
    Write-Host "容器 '$Container' 未运行，尝试启动..."
    docker start $Container 2>$null | Out-Null
    Start-Sleep -Seconds 3
    if (-not (Test-ContainerRunning $Container)) {
        throw "容器 '$Container' 未运行。请先执行：docker start $Container"
    }
}

$ready = $false
for ($i = 1; $i -le 15; $i++) {
    docker exec $Container mysqladmin ping -uroot -p$Password --silent 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    throw "MySQL 未就绪，请稍后再试：docker logs $Container"
}

Write-Host "应用 $sqlFile -> 容器 $Container ..."

$remoteSql = "/tmp/gateway-init.sql"
docker cp $sqlFile "${Container}:${remoteSql}"
cmd /c "docker exec $Container sh -c ""mysql -uroot -p$Password < $remoteSql"""
if ($LASTEXITCODE -ne 0) {
    throw "执行 init.sql 失败"
}
docker exec $Container rm -f $remoteSql 2>$null | Out-Null

Write-Host "完成。查看下游节点："
cmd /c "docker exec $Container mysql -uroot -p$Password -e ""SELECT id, http_url, grpc_addr, tcp_addr, weight FROM gateway.upstreams;"""
