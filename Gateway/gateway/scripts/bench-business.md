# FireFly 业务压测说明

`/health` 只测管道；业务压测打真实读写接口。

## 推荐场景

| 场景 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 用户资料 | GET | `/api/user/{id}` | Header 可选 `X-User-Id` |
| 笔记详情 | GET | `/api/note/{id}` | 含配图信息 |
| 赞数 | GET | `/api/social/like/{noteId}/count` | 读多 |
| 评论列表 | GET | `/api/social/comment/{noteId}` | 读多 |
| 通知列表 | GET | `/api/notify/list` | 需 `X-User-Id` |
| 发评论 | POST | `/api/social/comment` | 写库 + MQ，压力宜小 |
| 点赞 | POST | `/api/social/like/{noteId}` | 同用户只能赞一次，不适合高并发重复压 |

## WSL 一键读压测

服务与 Gateway 在同一侧（建议都在 WSL）后。经网关时 **必须带 JWT**（`api_required: true`）：

```bash
cd /mnt/d/A_Software/Java/SAVE/FireFly-book/Gateway/gateway/scripts
chmod +x bench-business.sh

# 经网关：自动 login 拿 token
USER=alice PASS=123456 NOTE_ID=10 ./bench-business.sh

# 或手动 TOKEN
TOKEN=eyJ... NOTE_ID=10 ./bench-business.sh

# 直连 content（不经网关、无 JWT）
BASE=http://127.0.0.1:9002 NOTE_ID=10 ./bench-business.sh
```

## 写压测（慎用）

```bash
RUN_WRITE=1 CONNECTIONS=10 DURATION=10s NOTE_ID=10 ./bench-business.sh
```

点赞不要用固定用户狂打：会大量「已点过赞」。若要压点赞，需准备大量 userId 或每次换笔记。

## 看什么指标

- **Requests/sec**：业务吞吐  
- **Latency Avg / 高分位**：用户体感  
- **Non-2xx / Socket errors**：失败率（DB、超时、限流 429、网关 503）

先读后写；先单接口再混合。
