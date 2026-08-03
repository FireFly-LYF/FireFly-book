# 00 · 项目全景：Java 要做哪些部分

## 本章目标

- 弄清「仿小红书」里哪些必须用 Java 做
- 知道每个服务干什么、谁先谁后
- 能画出：客户端 → 网关 → Java 服务 → 数据库

---

## 1. 产品功能对照表

| 用户感知功能 | 对应 Java 服务 | 优先级 |
|--------------|----------------|--------|
| 注册 / 登录 / 改资料 | user-service | P0 必做 |
| 发笔记、看笔记详情、改/删自己的笔记 | content-service | P0 必做 |
| 选图上传、拿到图片 URL | media-service | P0 必做 |
| 点赞、收藏、评论 | social-service | P1 |
| 「有人赞了我」通知 | notify-service | P1 |
| 关注流（刷到关注的人发的） | feed-service | P2 |
| 搜笔记 / 搜用户 | search-service | P2 可后做 |
| AI 审核 / 推荐 | Python（本教程不做） | 以后 |

**新手策略**：先打通 P0（能注册、能发带图笔记、能打开详情），再做赞评，再做 Feed。

---

## 2. 服务一览（只记职责）

```
user-service     人是谁、关注谁
content-service  笔记正文与状态
media-service    文件存在哪、URL 是什么
social-service   赞、藏、评（互动）
notify-service   通知消息
feed-service     「刷什么」列表怎么拼
search-service   搜索（ES）
```

原则：**一个服务一个数据库（或至少一套表前缀）**，不要所有表塞一个库还互相乱 join。新手阶段可以先「一个 MySQL 实例里建多个 database」，逻辑上仍然分开。

---

## 3. 请求怎么走（记这张图）

```
┌─────────┐     ┌──────────────┐     ┌─────────────────┐
│  App    │────▶│  Gateway     │────▶│ user-service    │──▶ MySQL(user)
│  / Web  │     │  :8080       │     │ :9001           │
└─────────┘     │  JWT / 限流  │     ├─────────────────┤
                │  按路径转发  │────▶│ content-service │──▶ MySQL(content)
                └──────────────┘     │ :9002           │
                       │             ├─────────────────┤
                       └────────────▶│ media / social… │
```

路径约定（后面网关章节会用到）：

| 路径前缀 | 转发到 |
|----------|--------|
| `/api/user/**` | user-service |
| `/api/note/**` | content-service |
| `/api/media/**` | media-service |
| `/api/social/**` | social-service |
| `/api/notify/**` | notify-service |
| `/api/feed/**` | feed-service |
| `/api/search/**` | search-service |

---

## 4. 每个服务「最少要实现」的接口

### user-service
- `POST /api/user/register` 注册
- `POST /api/user/login` 登录（若 JWT 由网关/本服务签发，见第 13 章）
- `GET /api/user/me` 当前用户资料
- `PUT /api/user/me` 改昵称头像
- `POST /api/user/follow/{id}` 关注
- `DELETE /api/user/follow/{id}` 取消关注
- `GET /api/user/{id}` 看别人主页

### content-service
- `POST /api/note` 发笔记
- `GET /api/note/{id}` 笔记详情
- `GET /api/note/user/{userId}` 某用户的笔记列表
- `PUT /api/note/{id}` 编辑
- `DELETE /api/note/{id}` 删除

### media-service
- `POST /api/media/upload` 上传（新手可先本地存盘）
- `GET /api/media/{id}` 查元数据 / URL

### social-service
- `POST /api/social/like/{noteId}` 点赞
- `DELETE /api/social/like/{noteId}` 取消
- `POST /api/social/collect/{noteId}` 收藏
- `POST /api/social/comment` 发评论
- `GET /api/social/comment/{noteId}` 评论列表

### notify-service
- `GET /api/notify/list` 我的通知
- `POST /api/notify/read` 标已读

### feed-service
- `GET /api/feed/following` 关注流

### search-service（可后做）
- `GET /api/search/note?q=`
- `GET /api/search/user?q=`

---

## 5. 建议的工程目录（以后你仓库会长这样）

```
FireFly-book/
  Gateway/                 ← 已有
  Java-Tutorial/           ← 你正在读的教程
  services/                ← 以后写代码放这里
    user-service/
    content-service/
    media-service/
    social-service/
    notify-service/
    feed-service/
    search-service/
```

**现在还不要求你立刻建齐**。跟着第 03 章先做一个 `hello-service`，再在第 06 章做成真正的 `user-service`。

---

## 6. 数据怎么分（概念）

| 库名（建议） | 主要表 |
|--------------|--------|
| `user` | user, user_profile, follow |
| `content` | note, note_media, note_tag |
| `media` | media_asset |
| `social` | note_like, note_collect, comment |
| `notify` | notification |
| `feed` | 可选；很多数据在 Redis |

服务之间 **用 userId / noteId 引用**，不复制整份用户资料到笔记表（最多冗余昵称做展示缓存，新手阶段可以暂时每次调 user 接口）。

---

## 7. 本章验收

能口头回答下面三题即可进入下一章：

1. P0 要先做哪三个服务？  
2. 客户端是直接打 Java 端口，还是先打 Gateway？  
3. 点赞应该放在 content-service 还是 social-service？为什么？

参考答案：1）user、content、media；2）先 Gateway；3）social，因为互动频率高、和正文生命周期不同。

---

下一章：[01-java-basics.md](./01-java-basics.md)
