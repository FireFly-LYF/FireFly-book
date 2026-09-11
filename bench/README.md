# FireFly 压测专用目录

与主工程隔离：脚本、Compose overlay、Go/Java 网关配置、SCG 对比实现与报告都在这里，不占用 `services/` / `deploy/` 业务空间。

## 保留报告（归档）

| 报告 | 内容 | 环境 |
|------|------|------|
| [`reports/gw-fair-20260910-120131.md`](./reports/gw-fair-20260910-120131.md) | Go / Java 网关公平对比 | WSL **4 vCPU / ~7.8Gi**，wrk `-t4 -c50 -d20s` |
| [`reports/biz-fair-20260910-125902.md`](./reports/biz-fair-20260910-125902.md) | 业务读公平对比（交替先后） | 同上 + 预热 |
| [`reports/cache-l1-ab-20260910-172645.md`](./reports/cache-l1-ab-20260910-172645.md) | **L1 关/开**业务压测对比（QPS 3936→5537） | 经 Go 网关；详见 off/on 分报告 |
| [`reports/nsfw-detector-20260911-125650.md`](./reports/nsfw-detector-20260911-125650.md) | NSFW **CPU** 端到端（Docker `vxlink/nsfw_detector`） | 容器 4CPU / 8GB |
| [`reports/nsfw-local-online-20260911-150141.md`](./reports/nsfw-local-online-20260911-150141.md) | NSFW **GPU** 端到端（本机 HTTP `/check`） | RTX 5060 Laptop |

`*.raw*` / `*.console*` 已 gitignore，不入库。

## 目录

```text
bench/
  compose/       docker-compose.bench.yml（端口映射）
                 docker-compose.cache-l1.yml（L1 A/B overlay）
                 docker-compose.social-solo.yml（评论直连）
                 docker-compose.java-gateway.yml
  config/        gateway-wsl-compare.yaml / gateway-wsl-middleware.yaml
  java-gateway/  Spring Cloud Gateway 对比实现
  scripts/       压测入口脚本
  reports/       归档报告（仅保留上表）
```

## 用法（仓库根目录，WSL）

```bash
# 1) 业务栈 + 映射端口（不启 Python AI）
docker compose -f deploy/docker-compose.yml \
  -f bench/compose/docker-compose.bench.yml \
  --env-file .env up -d

# 2) 网关公平对比
THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-gateway-fair.sh

# 3) 业务读对比
THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-business-fair.sh

# 4) Go 业务 P50/P99（Redis 中间件）
THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-go-business-latency.sh

# 5) L1 关/开 A/B
./bench/scripts/bench-cache-l1-ab.sh
```

Windows（NSFW）：

```powershell
# CPU 容器端到端
.\bench\scripts\bench-nsfw-detector.ps1

# GPU 本机线上同路径 HTTP
.\bench\scripts\bench-nsfw-local-online.ps1
```

## 脚本一览

| 脚本 | 作用 |
|------|------|
| `bench-gateway-fair.sh` | 网关对比 + 写报告 |
| `bench-business-fair.sh` | 业务读对比 + 写报告 |
| `bench-go-business-latency.sh` | Go 业务 QPS / P50 / P99 |
| `bench-cache-l1-ab.sh` | Caffeine L1 关/开对照 |
| `bench-social-comment-direct.sh` | 单服务直连评论 |
| `bench-nsfw-detector.ps1` | NSFW CPU Docker e2e |
| `bench-nsfw-local-online.ps1` / `bench_nsfw_local_online.py` | NSFW GPU HTTP e2e |
| `bench-nsfw-gpu.ps1` / `bench_nsfw_gpu_infer.py` | NSFW GPU 纯推理（辅助） |
| `seed-bench.sh` | 注册登录 + 造笔记 |
