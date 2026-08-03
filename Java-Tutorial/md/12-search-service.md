# 12 · search-service（可后做）

> 时间紧可跳过，先做第 13 章网关联调。  
> 搜索会引入 Elasticsearch，概念比 CRUD 多一截。

## 本章目标

**search-service**（端口 **9007**）：按关键词搜笔记标题/正文、搜用户昵称。

---

## 为什么还要单独服务？

MySQL `LIKE '%咖啡%'` 在数据量大时又慢又难相关度排序。  
ES 适合全文检索；MySQL 继续做「权威数据源」，ES 做「搜索副本」。

---

## 步骤 1：本地起 ES（Docker）

```powershell
docker run -d --name ff-es -p 9200:9200 -e "discovery.type=single-node" -e "xpack.security.enabled=false" elasticsearch:8.12.0
```

浏览器打开 http://127.0.0.1:9200 有 JSON 即成功。

---

## 步骤 2：同步策略（概念）

```
发笔记成功 →（以后用 MQ）→ search 写入/更新 ES 文档
删笔记 → 删 ES 文档
```

新手无 MQ 时：content 发笔记成功后 HTTP 调 search 的 `/inner/index`。

文档示例：

```json
{
  "id": 1,
  "userId": 2,
  "title": "周末探店",
  "content": "咖啡与猫",
  "coverUrl": "..."
}
```

---

## 步骤 3：接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/search/note?q=咖啡&page=1&size=10` | 搜笔记 |
| GET | `/api/search/user?q=小红` | 搜用户（数据可来自定时全量或 user 同步） |
| POST | `/api/search/inner/index` | 索引一篇笔记 |
| GET | `/health` | 探活 |

Java 客户端可用 **Elasticsearch Java API Client**；上手成本高的话，早期可用 ES 的 HTTP REST（`RestClient` 发 JSON）。

---

## 步骤 4：更简单的过渡方案

若暂时不想装 ES：

```sql
SELECT * FROM note
WHERE status=1 AND (title LIKE CONCAT('%', #{q}, '%') OR content LIKE CONCAT('%', #{q}, '%'))
ORDER BY id DESC LIMIT ...
```

放在 content-service 的 `/api/note/search` 也能交差，**务必在文档里标注这是临时方案**。

---

## 本章验收（做了 ES 的话）

- [ ] 新笔记能被搜到
- [ ] 删除后搜不到
- [ ] 空关键词有保护（直接返回错误或空）

下一章：对接 Gateway → [13-gateway-integration.md](./13-gateway-integration.md)
