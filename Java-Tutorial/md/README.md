# FireFly-book · Java 业务侧分步教程（新手向）

> 目标：在已有 **Go 网关** 后面，用 Java 做出仿小红书的核心业务（用户、笔记、互动、信息流等）。  
> 你是 Java 新手也没关系：按章节顺序做，每章有「今天要完成什么」和「怎么验收」。

---

## 你现在的位置

| 部分 | 状态 | 说明 |
|------|------|------|
| Gateway（Go） | 已有 | JWT、限流、负载均衡、服务注册 |
| Java 业务微服务 | **本教程** | 真正的「发笔记 / 点赞 / Feed」 |
| Python AI | 以后再做 | 审核、推荐，本文件夹不覆盖 |

---

## 学习路线（建议顺序）

```
第 0 周  概念 + 环境
  00 项目全景（先读）
  01 Java 最小必要知识
  02 开发环境安装

第 1 周  Spring Boot 入门
  03 第一个 Spring Boot 项目
  04 写 REST API（Controller）
  05 连接 MySQL + MyBatis

第 2 周  P0 能发笔记
  06 user-service（注册登录、资料）
  07 content-service（笔记 CRUD）
  08 media-service（上传与图片地址）

第 3 周  P1 互动
  09 social-service（赞 / 藏 / 评）
  10 notify-service（通知）

第 4 周  P2 信息流与搜索
  11 feed-service（关注流）
  12 search-service（ES 搜索，可略读）

第 5 周  联调与进阶
  13 对接 Gateway
  14 Redis 与消息队列入门
  15 总检查清单与常见坑
```

每天建议投入 **1～3 小时**。宁慢求懂，不要一次开很多服务。

---

## 文件夹目录

| 文件 | 内容 |
|------|------|
| [00-overview.md](./00-overview.md) | 仿小红书要做哪些 Java 部分、服务怎么分 |
| [01-java-basics.md](./01-java-basics.md) | 新手必会的 Java 语法（够用即可） |
| [02-env-setup.md](./02-env-setup.md) | JDK / Cursor / Maven / MySQL / Redis |
| [03-spring-boot-hello.md](./03-spring-boot-hello.md) | 第一个可运行的服务 |
| [04-rest-api.md](./04-rest-api.md) | Controller、DTO、统一返回格式 |
| [05-mysql-mybatis.md](./05-mysql-mybatis.md) | 建库建表、增删改查 |
| [06-user-service.md](./06-user-service.md) | 用户服务逐步实现 |
| [07-content-service.md](./07-content-service.md) | 笔记（内容）服务 |
| [08-media-service.md](./08-media-service.md) | 媒体 / 上传 |
| [09-social-service.md](./09-social-service.md) | 点赞收藏评论 |
| [10-notify-service.md](./10-notify-service.md) | 通知 |
| [11-feed-service.md](./11-feed-service.md) | 关注信息流 |
| [12-search-service.md](./12-search-service.md) | 搜索（可后做） |
| [13-gateway-integration.md](./13-gateway-integration.md) | 接到现有 Gateway |
| [14-redis-mq.md](./14-redis-mq.md) | 缓存与异步消息 |
| [15-checklist.md](./15-checklist.md) | 总清单、验收标准、常见错误 |
| [appendix-sql.md](./appendix-sql.md) | 全部建库 SQL 汇总（可一次执行） |

---

## 推荐技术栈（本教程统一用这个，少选型纠结）

| 用途 | 选型 | 版本建议 |
|------|------|----------|
| 语言 | Java | **17**（LTS） |
| 框架 | Spring Boot | **4.0.x / 4.1.x**（以 start.spring.io 当前可选为准） |
| 构建 | Maven | 3.9+ |
| ORM | MyBatis（或 MyBatis-Plus） | 与 Boot 匹配 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 接口测试 | Apifox / Postman / curl | 任选 |
| IDE | **Cursor**（装 Extension Pack for Java）或 IDEA | 本教程默认 Cursor |

初期 **一个服务一个 Spring Boot 工程**；等你写完 user + content，再考虑拆更多仓库。

---

## 和 Gateway 的关系（先记住一句）

```
浏览器/App  →  Gateway(:8080)  →  各个 Java 服务(:9001, :9002, ...)
```

- 登录态 JWT：优先在 **Gateway** 校验（你已有）。
- Java 服务：信任网关传来的用户身份（例如 Header `X-User-Id`），自己专注业务逻辑。
- 详细对接见 [13-gateway-integration.md](./13-gateway-integration.md)。

---

## 怎么用这份教程

1. 打开下一章，先看「本章目标」。
2. 按「步骤 1、2、3…」动手（复制代码后要改包名、端口）。
3. 做完「验收」再进下一章。
4. 卡住时先看该章「常见问题」，再查 [15-checklist.md](./15-checklist.md)。

准备好了？从 [00-overview.md](./00-overview.md) 开始。
