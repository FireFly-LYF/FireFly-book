# 15 · 总检查清单、学习日程与常见坑

## 一、Java 部分「要做完」的总清单

### P0 — 能用的最小闭环

- JDK17 / Maven / Cursor（Java 扩展）/ MySQL 就绪
- 理解 Controller → Service → Mapper
- **user-service**：注册、登录、资料、`X-User-Id`、关注
- **media-service**：上传、URL 可访问
- **content-service**：发笔记、详情、删自己的笔记
- 三服务均可 `/health`
- （可选）经 Gateway 访问 user + content

### P1 — 互动

- **social-service**：赞 / 藏 / 评
- **notify-service**：点赞或关注产生通知
- 前端或 Apifox 能走完：注册→上传→发笔记→点赞→看通知

### P2 — 信息流与搜索

- **feed-service**：关注流（读扩散）
- **search-service** 或 content 临时 LIKE 搜索
- Redis 缓存或点赞计数（至少一个）

### P3 — 工程化（有余力）

- 密码 BCrypt
- 统一全局异常处理
- MQ 异步通知
- 网关 JWT 注入 `X-User-Id`
- 配置外置、多环境 `application-dev.yml`
- 简单 Dockerfile 启动某个服务

---

## 二、建议 5 周日程（每周约 8～12 小时）


| 周   | 章节    | 产出              |
| --- | ----- | --------------- |
| 1   | 00～05 | hello + 注册落库    |
| 2   | 06～08 | 用户 + 媒体 + 笔记闭环  |
| 3   | 09～10 | 赞评 + 通知         |
| 4   | 11～13 | Feed + 网关联调     |
| 5   | 14～15 | Redis/MQ + 查漏补缺 |


---

## 三、每个服务端口与库（贴墙上）


| 服务              | 端口   | 数据库         |
| --------------- | ---- | ----------- |
| user-service    | 9001 | user        |
| content-service | 9002 | content     |
| media-service   | 9003 | media       |
| social-service  | 9004 | social      |
| notify-service  | 9005 | notify      |
| feed-service    | 9006 | 可无          |
| search-service  | 9007 | ES          |
| Gateway         | 8080 | gateway（已有） |


---

## 四、常见坑（几乎每个人都会遇到）


| 坑              | 处理                                   |
| -------------- | ------------------------------------ |
| 端口冲突           | 按上表改 `server.port`                   |
| MySQL 时区报错     | URL 加 `serverTimezone=Asia/Shanghai` |
| 中文乱码           | 库表 `utf8mb4`；JSON UTF-8              |
| 跨服务调不通         | 先 curl 单服务；查防火墙；看对方是否启动              |
| `X-User-Id` 为空 | Header 名写错；网关未转发自定义头                 |
| 上传 413         | 调大 Spring multipart 与网关限制            |
| 事务不生效          | `@Transactional` 是否公共方法、是否同类自调       |
| 依赖下不动          | Maven 阿里云镜像                          |
| IDEA 红字但能跑     | 再 Reload Maven；检查 JDK 模块是否 17        |


---

## 五、代码质量最低线（新手也要做到）

1. 响应里永不返回 password
2. 写接口必须校验登录（有 userId）
3. 改/删必须校验是本人资源
4. 每个服务有 `/health`
5. 重要操作能在 Apifox 一键复测

---

## 六、学完 Java 部分之后

1. 回头完善 Gateway 按路径路由与 JWT 用户注入
2. 再开 Python AI 仓：审核、标签、推荐
3. 需要时把公共 `ApiResponse` 抽成内部 jar，避免复制粘贴

---

## 七、文档索引

返回总目录：[README.md](./README.md)

从全景再读一遍：[00-overview.md](./00-overview.md)

---

你不需要一次做完所有服务。  
**本周只做成：注册 + 上传 + 发一篇带图笔记 + 自己能打开详情** —— 就已经超过很多「只看架构不写代码」的人了。加油。