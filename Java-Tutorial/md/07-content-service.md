# 07 · content-service 逐步实现

## 本章目标

独立工程 **content-service**（端口 **9002**），完成笔记的发布与查询。这是仿小红书的核心。

---

## 步骤 0：工程与库

| 项 | 值 |
|----|-----|
| 端口 | 9002 |
| 库名 | `content` |
| 前缀 | `/api/note` |

新建 Spring Boot 工程，依赖：Web、MyBatis、MySQL。  
复制一份 `ApiResponse`（或以后抽公共模块；新手先复制）。

---

## 步骤 1：建表

```sql
CREATE DATABASE IF NOT EXISTS content DEFAULT CHARACTER SET utf8mb4;
USE content;

CREATE TABLE IF NOT EXISTS `note` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `title`       VARCHAR(128) NOT NULL,
  `content`     TEXT         NOT NULL,
  `cover_url`   VARCHAR(512) DEFAULT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0草稿 1已发布 2审核中 3拒绝',
  `like_count`  INT          NOT NULL DEFAULT 0,
  `idem_key`    VARCHAR(64)  DEFAULT NULL COMMENT '客户端 Idempotency-Key',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user (`user_id`),
  KEY idx_created (`created_at`),
  UNIQUE KEY uk_note_idem (`user_id`, `idem_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 笔记关联的图片（media-service 返回的 url 或 id）
CREATE TABLE IF NOT EXISTS `note_media` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `note_id`    BIGINT       NOT NULL,
  `media_url`  VARCHAR(512) NOT NULL,
  `sort_no`    INT          NOT NULL DEFAULT 0,
  KEY idx_note (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

说明：

- `user_id` 只存 ID，不存昵称（需要展示时调 user-service 或以后做缓存）。
- `like_count` 可先由 social 异步更新；新手可暂时在点赞时再改，或先不做实时准确。

---

## 步骤 2：接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/note` | 发笔记 |
| GET | `/api/note/{id}` | 详情 |
| GET | `/api/note/user/{userId}` | 某用户笔记列表 |
| PUT | `/api/note/{id}` | 编辑（仅作者） |
| DELETE | `/api/note/{id}` | 删除（仅作者） |
| GET | `/health` | 探活 |

发笔记 body 示例：

```json
{
  "title": "周末探店",
  "content": "这家咖啡真不错……",
  "coverUrl": "http://127.0.0.1:9003/files/xxx.jpg",
  "mediaUrls": [
    "http://127.0.0.1:9003/files/xxx.jpg",
    "http://127.0.0.1:9003/files/yyy.jpg"
  ]
}
```

作者从 Header 取：`X-User-Id`。发帖可再带 `Idempotency-Key`（UUID）：同一用户同一钥匙只成功一次，超时重试不会插出两篇。

---

## 步骤 3：实现顺序（按天）

### Day 1：CRUD 骨架

1. `Note` 实体 + `NoteMapper`（insert / findById / listByUserId / update / delete）
2. `POST /api/note` 只插 `note` 表（先不管 media）
3. `GET /api/note/{id}` 能查到

### Day 2：图片列表

1. `NoteMediaMapper`
2. 发笔记时事务内：先插 note，再循环插 note_media
3. 详情接口把 mediaUrls 一并返回

开启事务：Service 方法加 `@Transactional`，启动类所在应用已有 Spring 事务即可。

```java
@Transactional
public Long create(Long userId, CreateNoteRequest req) {
    // insert note
    // insert medias
    return note.getId();
}
```

### Day 3：权限

- 编辑/删除前：`note.userId` 必须等于 `X-User-Id`
- 否则返回 `40301 无权操作`

### Day 4：列表分页（简单版）

```text
GET /api/note/user/{userId}?page=1&size=10
```

SQL：`LIMIT #{size} OFFSET #{offset}`，`offset = (page-1)*size`。

---

## 步骤 4：Mapper 示例片段

```java
@Insert("INSERT INTO note(user_id, title, content, cover_url, status) " +
        "VALUES(#{userId}, #{title}, #{content}, #{coverUrl}, #{status})")
@Options(useGeneratedKeys = true, keyProperty = "id")
int insert(Note note);

@Select("SELECT * FROM note WHERE id=#{id}")
Note findById(Long id);

@Select("SELECT * FROM note WHERE user_id=#{userId} AND status=1 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
List<Note> listByUser(@Param("userId") Long userId,
                      @Param("limit") int limit,
                      @Param("offset") int offset);
```

---

## 步骤 5：与 media-service 的关系

推荐流程（P0）：

```
1. 客户端先调 media-service 上传，拿到 url
2. 再调 content-service 发笔记，带上 url 列表
```

content **不存文件**，只存 URL 字符串。这样服务边界清晰。

---

## 步骤 6：状态机（先简单）

| status | 含义 | 新手做法 |
|--------|------|----------|
| 0 | 草稿 | 可后做 |
| 1 | 已发布 | 发笔记直接 1 |
| 2 | 审核中 | 以后接 AI |
| 3 | 拒绝 | 以后接 AI |

现在全部用 `1` 即可。

---

## 步骤 7：自测

```powershell
# 发笔记
curl -X POST http://127.0.0.1:9002/api/note `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1" `
  -d "{\"title\":\"第一篇\",\"content\":\"你好小红书\",\"coverUrl\":\"\",\"mediaUrls\":[]}"

# 详情
curl http://127.0.0.1:9002/api/note/1
```

---

## 本章验收

- [ ] 独立库 `content`，服务端口 9002
- [ ] 能发、能查、能删自己的笔记
- [ ] 不能删别人的笔记
- [ ] 详情可带图片 URL 列表（可先空数组）

下一章：上传图片 → [08-media-service.md](./08-media-service.md)
