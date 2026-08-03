# 10 · notify-service 逐步实现

## 本章目标

**notify-service**（端口 **9005**）：把「有人赞了你 / 评论了你 / 关注了你」变成站内通知列表。

---

## 步骤 0：工程与库

| 项 | 值 |
|----|-----|
| 端口 | 9005 |
| 库 | `notify` |
| 前缀 | `/api/notify` |

---

## 步骤 1：建表

```sql
CREATE DATABASE IF NOT EXISTS notify DEFAULT CHARACTER SET utf8mb4;
USE notify;

CREATE TABLE IF NOT EXISTS `notification` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '接收者',
  `from_user_id` BIGINT      NOT NULL COMMENT '触发者',
  `type`        VARCHAR(32)  NOT NULL COMMENT 'LIKE/COMMENT/FOLLOW',
  `ref_id`      BIGINT       DEFAULT NULL COMMENT '笔记或评论 id',
  `content`     VARCHAR(256) DEFAULT NULL,
  `is_read`     TINYINT      NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 步骤 2：接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notify/list` | 我的通知（分页） |
| POST | `/api/notify/read` | 全部已读或按 id 已读 |
| POST | `/api/notify/inner/create` | **内部接口**：别的服务调用来创建通知 |
| GET | `/health` | 探活 |

`inner/create` body 示例：

```json
{
  "userId": 1,
  "fromUserId": 2,
  "type": "LIKE",
  "refId": 10,
  "content": "赞了你的笔记"
}
```

注意：正式环境应对 `/inner/**` 做内网限制或服务 Token；新手本机可先开放。

---

## 步骤 3：谁来创建通知？

两种接法：

### 接法 A：同步 HTTP（先做这个）

在 social-service 点赞成功后：

```text
RestTemplate.post → http://127.0.0.1:9005/api/notify/inner/create
```

关注成功时同理（user-service 调 notify）。

要拿到「笔记作者是谁」：social 需要知道 note 的 userId——可：

- 点赞请求里让前端带 `authorId`（不严谨但简单），或
- social 调 content `GET /api/note/{id}` 取 `userId`

### 接法 B：MQ 异步（第 14 章）

social 发消息 `LIKE_CREATED`，notify 消费后写库。更优，稍后再学。

---

## 步骤 4：列表与已读

```text
GET /api/notify/list?page=1&size=20
Header: X-User-Id
按 created_at DESC
```

已读：

```json
POST /api/notify/read
{ "ids": [1,2,3] }
```

或 `{"all": true}` 将该用户全部标已读。

---

## 步骤 5：自测

1. 用户 1 的笔记被用户 2 点赞  
2. 用户 1 拉通知列表能看到一条 LIKE  
3. 标已读后 `is_read=1`  

---

## 本章验收

- [ ] 能写入通知
- [ ] 只能查自己的列表
- [ ] 已读状态可变

下一章：关注流 → [11-feed-service.md](./11-feed-service.md)
