# assistant-service

用户搜索时的 AI 问答：站内笔记（search-service）+ 可选网络搜索 + LLM 综合回答（支持 SSE 流式）。

## 代码怎么读（推荐顺序）

按「请求从上往下」读：

```text
1. routes/search.py            ← HTTP 入口：收请求、鉴权、写 JSON/SSE
2. agent/registry.py           ← workflow 注册与调度（当前仅 search）
3. workflows/search_answer.py  ← 搜索问答 workflow：准备材料 → LLM → 返回/推流
4. agent/llm.py                ← Agent 核心 LLM 调用
5. tools/notes.py / web_search.py / web_safety.py
   ← 搜笔记、网络搜索；Web 结果敏感词/规则 + 域名黑名单过滤
```

目录分层（agent 骨架 + 多 workflow，无自主决策 loop）：

| 路径 | 职责 |
|------|------|
| `main.py` / `app.py` | 进程入口、FastAPI 组装、网关鉴权 |
| `routes/` | HTTP 路由（传输层，不含业务编排） |
| `agent/` | LLM 核心调用、流式基础设施、共享上下文、workflow 注册 |
| `workflows/` | 确定性业务编排（search_answer；未来 recommend、write_note 等） |
| `tools/` | search-service / 网页检索；`web_safety` 过滤不合规结果 |
| `schemas/` | 请求/响应 JSON 形状 |
| `settings.py` | 环境变量 |

流式路径简述：`routes/search.py` → `agent.registry.invoke_stream("search")` → `workflows/search_answer` → `agent/llm.iter_answer_chunks`（经 `agent/sync_stream` 线程桥）→ `agent/citations` 核对引用 → SSE `meta` / `delta` / `done`。

### 溯源约定

- 材料固定标号：`[笔记1]`、`[网络2]`（禁止模糊来源说法）
- 模型须在文末输出 `usedSources: [笔记1, 网络2]`（或 `[]`）
- 服务端只认上述标号，与本次候选集合求交；接口 `noteSources` / `webSources` **仅为实引子集**
- 无检索材料时 `ungrounded=true`，回答带「未检索到依据，以下是AI补充结果」

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
| `WEB_BLOCKED_DOMAINS` | 追加域名黑名单（逗号分隔，含子域匹配） |
| `WEB_BLOCKED_KEYWORDS` | 追加敏感词（逗号分隔，扫标题/摘要/URL） |

Web 结果在进入 LLM 前经 `tools/web_safety.py` 过滤：内置色情/暴力等敏感词与规则，以及高风险域名黑名单；命中则丢弃该条。

## 启动

```powershell
cd services-ai
pip install -e ./firefly-ai-common -e ./assistant-service
python -m assistant_service.main
```
