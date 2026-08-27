# assistant-service

用户搜索时的 AI 问答：站内笔记（search-service）+ 可选网络搜索 + LLM 综合回答（支持 SSE 流式）。

## 代码怎么读（推荐顺序）

按「请求从上往下」读：

```text
1. routes/search.py            ← HTTP 入口：收请求、鉴权、写 JSON/SSE
2. agent/registry.py           ← workflow 注册与调度（当前仅 search）
3. workflows/search_answer.py  ← 搜索问答 workflow：准备材料 → LLM → 返回/推流
4. agent/llm.py                ← Agent 核心 LLM 调用
5. tools/notes.py 等           ← 可复用工具：搜笔记、网络搜索
```

目录分层（agent 骨架 + 多 workflow，无自主决策 loop）：

| 路径 | 职责 |
|------|------|
| `main.py` / `app.py` | 进程入口、FastAPI 组装、网关鉴权 |
| `routes/` | HTTP 路由（传输层，不含业务编排） |
| `agent/` | LLM 核心调用、流式基础设施、共享上下文、workflow 注册 |
| `workflows/` | 确定性业务编排（search_answer；未来 recommend、write_note 等） |
| `tools/` | search-service / 网页等外部能力 |
| `schemas/` | 请求/响应 JSON 形状 |
| `settings.py` | 环境变量 |

流式路径简述：`routes/search.py` → `agent.registry.invoke_stream("search")` → `workflows/search_answer` → `agent/llm.iter_answer_chunks`（经 `agent/sync_stream` 线程桥）→ SSE `meta` / `delta` / `done`。

### 扩展新 workflow

1. 在 `workflows/` 新增模块（复用 `tools/`、`agent/llm`）
2. 在 `agent/registry.py` 的 `WORKFLOWS` / `STREAM_WORKFLOWS` 注册
3. 在 `routes/` 增加对应 HTTP 入口（或后续统一 `/api/ai/assist` + intent 路由）

## API

经 Gateway：

- `POST /api/ai/search` — 一次性 JSON
- `POST /api/ai/search/stream` — SSE 流式（需登录 JWT）

```json
{
  "query": "上海周末美食",
  "includeWeb": true,
  "noteLimit": 5,
  "noteCandidates": [
    { "id": 1, "title": "...", "content": "...", "coverUrl": "/files/..." }
  ]
}
```

前端传入 `noteCandidates` 时，assistant **不再**重复调用 search-service。

## 环境变量

| 变量 | 说明 |
|------|------|
| `ASSISTANT_SERVICE_PORT` | 默认 `9102` |
| `SEARCH_BASE_URL` | search-service，默认 `http://127.0.0.1:9007` |
| `INTERNAL_HMAC_SECRET` | 与网关 / Java 一致 |
| `LLM_BASE_URL` | OpenAI 兼容 API 根地址 |
| `LLM_API_KEY` | 留空则回退「检索摘要」模式 |
| `LLM_MODEL` | 默认见 `.env.example` |
| `WEB_SEARCH_ENABLED` | 默认可关（国内网络搜索常慢） |

## 启动

```powershell
cd services-ai
pip install -e ./firefly-ai-common -e ./assistant-service
python -m assistant_service.main
```
