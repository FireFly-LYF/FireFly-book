# 09 · social-service 逐步实现

## 本章目标

**social-service**（端口 **9004**）：点赞、收藏、评论。与笔记正文分离，便于以后单独扩容。

---

## 步骤 0：工程与库

| 项 | 值 |
|----|-----|
| 端口 | 9004 |
| 库 | `social` |
| 前缀 | `/api/social` |

---

## 步骤 1：建表

```sql
CREATE DATABASE IF NOT EXISTS social DEFAULT CHARACTER SET utf8mb4;
USE social;

CREATE TABLE IF NOT EXISTS `note_like` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT NOT NULL,
  `user_id`    BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_like (`note_id`, `user_id`),
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `note_collect` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT NOT NULL,
  `user_id`    BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_collect (`note_id`, `user_id`),
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `comment` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT NOT NULL,
  `user_id`    BIGINT NOT NULL,
  `parent_id`  BIGINT DEFAULT NULL COMMENT '回复哪条评论，可空',
  `content`    VARCHAR(512) NOT NULL,
  `idem_key`   VARCHAR(64) DEFAULT NULL COMMENT '客户端 Idempotency-Key',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_note (`note_id`),
  UNIQUE KEY uk_comment_idem (`user_id`, `idem_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`UNIQUE KEY` 保证同一用户对同一笔记只能赞一次。评论/发帖用 `Idempotency-Key` 防客户端重试插出两条。

---

## 步骤 2：接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/social/like/{noteId}` | 点赞 |
| DELETE | `/api/social/like/{noteId}` | 取消赞 |
| GET | `/api/social/like/{noteId}/count` | 点赞数 |
| GET | `/api/social/like/{noteId}/me` | 我是否已赞 |
| POST | `/api/social/collect/{noteId}` | 收藏 |
| DELETE | `/api/social/collect/{noteId}` | 取消收藏 |
| POST | `/api/social/comment` | 发评论 |
| GET | `/api/social/comment/{noteId}` | 评论列表 |
| GET | `/health` | 探活 |

发评论 body：

```json
{
  "noteId": 1,
  "content": "写得真好",
  "parentId": null
}
```

---

## 步骤 3：点赞实现要点

```text
1. 从 X-User-Id 取用户
2. INSERT note_like；若唯一键冲突 → 已点过赞
3. （可选）通知笔记作者 —— 见 notify 章
4. （可选）通知 content 更新 like_count —— 新手可先每次 COUNT(*)
```

取消赞：`DELETE` 对应行。

统计：

```sql
SELECT COUNT(1) FROM note_like WHERE note_id = #{noteId}
```

---

## 步骤 4：要不要调用 content-service？

| 做法 | 优点 | 缺点 |
|------|------|------|
| 每次 COUNT | 实现简单 | 高并发压力大 |
| 点赞时 HTTP 调 content 改计数 | 详情页读自己库快 | 服务耦合、失败要补偿 |
| MQ 异步改计数 | 解耦 | 要学消息队列 |

**新手建议**：先 COUNT；详情页需要赞数时，前端同时调 social 的 count 接口，或 content 详情里再请求一次 social（同步 HTTP）。

用 `RestTemplate` 或 `WebClient` 调其他服务——可放到学完第 11、14 章后再做。

---

## 步骤 5：评论列表

按 `created_at ASC` 或 `id ASC` 返回；先不做楼中楼树形，扁平列表即可：有 `parentId` 前端自己拼。

校验：`content` 不能空白，长度 ≤ 512。发评带 `Idempotency-Key`，撞 `uk_comment_idem` 则返回第一次那条。

---

## 步骤 6：自测脚本思路

1. 用户 1 发笔记（content）  
2. 用户 2 点赞、评论  
3. count = 1，评论列表有 1 条  
4. 用户 2 取消赞，count = 0  
5. 用户 2 再赞一次成功  

---

## 本章验收

- [ ] 点赞受唯一约束保护（不会插两条）
- [ ] 取消赞有效
- [ ] 评论可发可查
- [ ] 所有写接口依赖 `X-User-Id`

下一章：通知 → [10-notify-service.md](./10-notify-service.md)
