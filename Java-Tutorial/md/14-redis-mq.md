# 14 · Redis 与消息队列入门

## 本章目标

在不会被细节淹死的前提下，学会：

1. 用 Redis 做缓存 / 点赞计数
2. 用消息队列做「异步通知、异步更新 Feed/搜索」

这两样是微服务里最常见的进阶件。**P0 可以不做**；P1/P2 再加。

---

## 一、Redis

### 1. 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

### 2. 典型用法

**缓存用户资料：**

```text
Key: user:info:{id}
Value: JSON
TTL: 10 分钟
```

读资料时：先 Redis → 没有再 MySQL → 回写 Redis。  
改资料时：更新 MySQL 后 **删缓存**（简单稳妥）。

**点赞计数：**

```text
Key: note:like:count:{noteId}
INCR / DECR
```

同时仍要落库 `note_like`（防丢）；Redis 作热数据。

**防重复点赞（可选）：**

```text
Key: note:liked:{noteId}:{userId}
SET NX + TTL 或直接用 Set：note:liked:users:{noteId}
```

### 3. 新手练习（已在 social-service 落地）

点赞：`INSERT note_like` 成功后 `INCR note:like:count:{noteId}`（无 key 则按 DB `COUNT` 回填）。  
取消：`DELETE` 成功后 `DECR`。  
`GET /api/social/like/{noteId}/count` 优先读 Redis，未命中再查库并回写（TTL 24h）。

**用户资料 / 笔记 / 列表（已落地）：**

```text
Key: user:info:{id}                          TTL 10 分钟
Key: note:info:{id} / note:media:{id}        TTL 10 分钟
Key: note:list:{userId}:wall:{page}:{size}
Key: note:list:{userId}:latest:{perUser}
Key: follow:followingIds:{id} / follow:followerIds:{id}
Key: note:comments:{noteId}
Key: user:liked:{id}:{page}:{size}
Key: user:collected:{id}:{page}:{size}
Key: notify:inbox:{userId}:{page}:{size}
```

读：先 Redis → 未命中查库并回写。写：更新 MySQL 后 **删缓存**（列表按用户/笔记前缀整段失效）。  
`requireOwned` / `requireUser` 走详情缓存，不再直打 mapper。Redis 挂了则直接打 MySQL。

---

## 二、消息队列（选一个学）

| 选型 | 说明 |
|------|------|
| RabbitMQ | 入门友好，文档多 |
| RocketMQ | 国内业务常见 |
| Kafka | 吞吐高，稍重 |

本地可用 Docker 起 RabbitMQ：

```powershell
docker run -d --name ff-rabbit -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

管理台：http://127.0.0.1:15672 （默认 guest/guest）

### 典型事件

| 事件 | 生产者 | 消费者 |
|------|--------|--------|
| `note.created` | content | feed缓存预热、search 索引、AI 审核 |
| `like.created` | social | notify |
| `follow.created` | user | notify |

### 好处

- social 点赞接口更快（不用等 notify 写完）
- notify 挂了消息还在，恢复后继续处理
- 以后加「统计服务」只需多挂一个消费者

### 学习节奏

1. 先会发一条消息、收一条消息打日志  
2. 再把 notify 的创建逻辑挪到消费者  
3. 最后再加失败重试、死信队列（可查官方入门）

Spring 可用 `spring-boot-starter-amqp`（RabbitMQ）。

---

## 三、和本项目的推荐落地顺序

```
1. Redis：用户资料缓存
2. Redis：点赞计数
3. MQ：点赞 → 通知
4. MQ：发笔记 → 搜索索引
```

不要一上来把所有链路都改成异步，不好排查。

---

## 本章验收

- [x] 能用代码向 Redis set/get（social-service 点赞计数）
- [ ] 能说清「缓存与数据库谁为准」（DB 为准）
- [ ] 能说清 MQ 解决什么问题（解耦、异步、削峰）

下一章：总清单 → [15-checklist.md](./15-checklist.md)
