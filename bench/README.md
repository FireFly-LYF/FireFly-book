# FireFly 压测专用目录

与主工程隔离：脚本、Compose overlay、Go/Java 网关配置、SCG 对比实现与报告都在这里，不占用 `services/` / `deploy/` 业务空间。

## 保留报告（最新）

| 报告 | 内容 | 环境 / wrk |
|------|------|------------|
| [`reports/gw-fair-20260910-120131.md`](./reports/gw-fair-20260910-120131.md) | 网关：直连 / Go / Java（含 Avg/Max/timeout） | WSL **4 vCPU / ~7.8Gi**，`-t4 -c50 -d20s` |
| [`reports/biz-fair-20260910-125902.md`](./reports/biz-fair-20260910-125902.md) | 业务读：交替先后（含 Avg/Max/timeout） | 同上 + 预热 `-t2 -c10 -d3s` |

注：当时未加 `wrk --latency`，报告中的尾延迟为 **Max**，不是 P99。

## 目录

```text
bench/
  compose/       docker-compose.bench.yml（端口映射）
                 docker-compose.java-gateway.yml（可选容器跑 SCG）
  config/        gateway-wsl-compare.yaml（Go 关限流/熔断）
                 gateway-compose.bench.yaml（Docker Gateway 高限流，可选）
  java-gateway/  Spring Cloud Gateway 对比实现
  scripts/       压测入口脚本
  reports/       报告（*.raw* 已 gitignore）
```

## 用法（仓库根目录，WSL）

```bash
# 1) 业务栈 + 映射 9001/9002/9004/9005（不启 Python AI）
docker compose -f deploy/docker-compose.yml \
  -f bench/compose/docker-compose.bench.yml \
  --env-file .env up -d

# 2) 网关公平对比（停 Docker Gateway，本机起 Go + Java）
THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-gateway-fair.sh

# 3) 业务读对比
THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-business-fair.sh
```

```bash
# 可选：打包 Java 网关
cd bench/java-gateway && mvn -DskipTests package
```

恢复 Docker Gateway：`docker start firefly-gateway-1`

## 脚本一览

| 脚本 | 作用 |
|------|------|
| `bench-gateway-fair.sh` | 一键网关对比 + 写报告 |
| `bench-business-fair.sh` | 一键业务读对比 + 写报告 |
| `bench-gateway-compare.sh` | wrk：直连 / Go / Java |
| `bench-business-compare.sh` | wrk：业务接口交替对比 |
| `seed-bench.sh` | 注册登录 + 造笔记 |
| `bench-hey.ps1` | 旧 hey 脚本（Windows，可选） |
