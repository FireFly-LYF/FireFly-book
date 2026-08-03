# 11 · feed-service 逐步实现

## 本章目标

**feed-service**（端口 **9006**）：实现「关注流」——刷到我关注的人发的笔记。

推荐流（算法）留给以后的 Python AI；这里只做 **关注时间线**。

---

## 步骤 0：两种经典算法（先懂概念）

| 名称 | 做法 | 适合 |
|------|------|------|
| 读扩散（pull） | 打开 Feed 时，查关注列表，再查这些人最新笔记，合并排序 | 实现简单，粉丝多的大 V 也还行（读时算） |
| 写扩散（push） | 发笔记时，把笔记 id 推进每个粉丝的收件箱 | 读很快，大 V 发笔记很贵 |

**新手请用读扩散（pull）**，无需额外 Feed 表也能做。

---

## 步骤 1：工程

| 项 | 值 |
|----|-----|
| 端口 | 9006 |
| 库 | 可不建库（纯编排） |
| 前缀 | `/api/feed` |

依赖：Web。用 `RestTemplate` 调 user + content。

`application.yml`：

```yaml
server:
  port: 9006

app:
  user-base-url: http://127.0.0.1:9001
  content-base-url: http://127.0.0.1:9002
```

---

## 步骤 2：你需要 user-service 多一个接口

若还没有，在 user-service 增加：

```text
GET /api/user/me/following-ids
Header: X-User-Id
返回: [2, 5, 9]   // 我关注的人的 id 列表
```

实现：`SELECT followee_id FROM follow WHERE follower_id = ?`

---

## 步骤 3：content 侧配合

需要「按多个作者查最新笔记」，两种做法：

### 简单做法（笔记少时）

对每个 followeeId 调：

```text
GET /api/note/user/{userId}?page=1&size=5
```

再在内存里按 `createdAt` 合并排序，取前 20 条。

### 稍好做法

content 增加：

```text
POST /api/note/feed/query
Body: { "userIds": [1,2,3], "size": 20 }
```

SQL 类似：

```sql
SELECT * FROM note
WHERE user_id IN (...) AND status = 1
ORDER BY created_at DESC
LIMIT 20
```

新手用「简单做法」即可，关注人数控制在测试数据几十以内。

---

## 步骤 4：Feed 接口

```text
GET /api/feed/following?size=20
Header: X-User-Id
```

流程：

```
1. 调 user 获取 followingIds
2. 若为空 → 返回空列表（可提示去发现页，发现页以后做）
3. 调 content 拉取这些作者的笔记
4. 排序、裁剪
5. 返回笔记摘要列表
```

返回字段建议：`noteId, userId, title, coverUrl, createdAt`（详情再点进 content）。

---

## 步骤 5：RestTemplate 配置

```java
@Configuration
public class HttpConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

注意：解析对方返回的 `ApiResponse` 时，要用能嵌套的类型，或先拿 `Map` 再取 `data`。新手可暂时让被调服务多提供一个内部简化 JSON；或两端统一结构仔细反序列化。

调试技巧：先用浏览器/curl 分别调通 user、content，再写聚合代码。

---

## 步骤 6：自测场景

1. 用户 A 关注用户 B、C  
2. B、C 各发 2 篇笔记  
3. A 调 `/api/feed/following` 应看到 B、C 的笔记，且时间新的在前  
4. A 未关注的人的笔记不应出现  

---

## 步骤 7：以后优化方向（了解）

- 结果缓存到 Redis（第 14 章）
- 写扩散 + 收件箱
- 推荐流：调 Python 排序服务

---

## 本章验收

- [ ] 关注流只含关注作者的笔记
- [ ] 按时间大致倒序
- [ ] 无关注时返回空数组而不是报错

下一章：搜索（可后做）→ [12-search-service.md](./12-search-service.md)
