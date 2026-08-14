# FireFly-book 后端上线风险清单

> 基于当前仓库实码审查。整体适合教程/联调，**直接公网暴露风险高**。  
> 优先级：P0 上线必拦 → P1 流量上来会痛 → P2 长期债。

---

## P0｜身份与内网边界

| ID | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|----------|----------|----------|
| S1 | Java 服务只信 `X-User-Id`，自身不验 JWT | 各服务 Controller；网关 `proxy/http.go` 清头再注入 | ~~公网旁路~~：**已绑 `127.0.0.1`** + **网关 HMAC(`X-Gateway-Ts/Sign`)**；同机无密钥仍难伪造 | 密钥外置；时钟同步；可选再加强 mTLS |
| S2 | ~~`/api/*/inner/**` 经网关对登录用户开放~~ **已缓解** | 网关 `BlockList`：显式 `/api/search/inner`、`/api/notify/inner` + 通配 `/api/<svc>/inner/**` | 经网关调用返回 403；**旁路直连下游仍可打**（见 S1） | 保持黑名单；下游仍应内网隔离 / 服务账号 |
| S3 | JWT / DB / MQ 密钥口令硬编码进仓库 | 各 `application.yml`、`Gateway/.../gateway.yaml` | 泄露即可伪造 Token、接管库与 MQ | 环境变量 / Secret Manager；禁止默认值进生产镜像 |
| S4 | 密码 MD5 无盐 | `UserService.java`（注释已写应用 BCrypt） | 库泄露后易彩虹表/撞库 | BCrypt/Argon2 + 存量迁移 |
| S5 | ~~`/gateway/login` 无真实凭证~~ **已禁用** | `gateway.go` 不再注册该路由；`login.go` 保留未挂载 | 无法再经此接口拿弱 JWT | 运维鉴权仍待拆分（见 S6）；Admin 前端登录需另接方案 |
| S6 | 业务用户 JWT 可访问网关运维 API | `gateway.go` admin 组与业务共用 `JWTSecret()` | 登录用户可注册/摘除上游，流量劫持或全站不可用 | 运维通道独立密钥/mTLS/RBAC |
| S7 | ~~CORS 反射任意 Origin~~ **已改为白名单** | `cors.go` + `gateway.yaml` `cors.allowed_origins` | 未命中 Origin 不写 Allow-Origin；预检 403 | 生产把名单换成正式前端域名；空名单=最严 |
| S8 | `/files/**` 无强制鉴权 + 本地静态挂载 | `gateway.yaml` `media-files auth: false`；media `WebConfig` | 猜到 UUID 即可读未公开图片 | 对象存储 + 签名 URL；或鉴权代理 |
| S9 | 媒体仅靠 `Content-Type` 前缀校验 | `MediaService.save` | 伪装 MIME 上传非图/恶意内容；扩展名污染 | 魔数校验、扩展名白名单、服务端重编码 |

---

## P0｜MQ 与最终一致性

| ID | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|----------|----------|----------|
| M1 | 消费者吞异常后 ACK → 消息永久丢失 | `SearchEventListener` 等 catch 只打日志 | ES/通知短暂故障后索引永久落后 | 失败抛出重试；或 nack |
| M2 | 无 DLQ / 无重试退避配置 | 各 `RabbitConfig` 仅 durable queue | 毒消息/瞬时故障无法隔离恢复 | DLX/DLQ + 有限重试 + 告警 |
| M3 | 生产者发送失败只 warn，主流程仍成功 | `NoteEventPublisher`、`NotifyEventPublisher` 等 | 搜索/通知静默缺失，无补偿 | Transactional Outbox 或本地重试表 + 对账 |

---

## P0｜部署与媒体配置

| ID | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|----------|----------|----------|
| D1 | 全本地硬编码：`127.0.0.1`、Windows 路径、固定端口 | 各 `application.yml`；`media.storage-dir: D:/FireFlyData/media` | 容器/多机无法启动或裂图 | 环境变量、`prod` profile、可挂载卷/对象存储 |
| D2 | 无生产 profile；Docker 未编排 Java 业务服务 | 无 `application-prod.yml`；compose 仅网关演示 | 「本机能跑、生产崩」 | 完整 compose/K8s；配置外置 |

---

## P1｜韧性与性能

| ID | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|----------|----------|----------|
| R1 | Feed 读扩散：关注数 × HTTP | `FeedService.followingFeed` → `contentClient.listByUser` | 关注多则延迟线性恶化，易拖垮 | 批量拉笔记 / 写扩散时间线；限制扫描上限 |
| R2 | Java 侧 `RestTemplate` 裸建、无超时/熔断 | `HttpConfig` / `AppConfig` 等 `new RestTemplate()` | 下游挂起占满线程池，雪崩 | 连接/读超时；Resilience4j |
| R3 | 网关 ReverseProxy 无显式超时 | `proxy/http.go` | 慢上游占满代理连接 | Transport Timeout + 与熔断联动 |
| R4 | 限流阈值极大（形同关闭） | `gateway.yaml` `rate: 100000` | 登录/上传/搜索易被刷 | 按路由/用户/IP 分级限流 |
| R5 | ES 搜索带 wildcard `*q*` | `ElasticsearchRestClient.search` | 大数据量下 CPU/延迟差，慢查询打挂 | IK 分词；限 q 长度；禁前导通配 |
| R6 | 无 Hikari 显式调优；业务缓存几乎空白 | 各 datasource 仅基础配置 | 流量上涨时连接池/热点不足 | 按实例调池；热点用户/笔记缓存 |

---

## P1｜数据一致性与幂等

| ID | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|----------|----------|----------|
| C1 | 笔记 update 未与 create 对齐事务 + afterCommit | `NoteService.update` | 更新失败仍可能已发 MQ，或边界不清 | 与 create 对齐 `@Transactional` + afterCommit |
| C2 | 点赞「先查后插」竞态 | `LikeService.like`；表有 `uk_like` | 并发双击可能 500 而非业务友好错误 | 捕获 DuplicateKey / INSERT IGNORE |
| C3 | 通知表无幂等键 | `NotifyService.create` 每次 insert | MQ 重投产生重复 LIKE/FOLLOW 通知 | `(type, from_user_id, ref_id)` 唯一或消费去重 |
| C4 | 关键写操作无 `Idempotency-Key` | 发帖/评论等 | 客户端重试导致重复内容 | 幂等表或唯一业务键 |

---

## P1｜可观测性

| ID | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|----------|----------|----------|
| O1 | 无 Actuator / Micrometer / Tracing | 各服务 `pom.xml` | 难定位延迟、错误率、依赖健康 | `/actuator/health` + 指标 + TraceId 贯穿 |
| O2 | Health 仅为字符串 `"OK"` | 各 `HealthController` | 网关认为健康，但 DB/MQ 已挂 | 探真实依赖；区分 liveness/readiness |
| O3 | Java 侧无统一结构化字段（userId、requestId） | 对比网关已有 RequestLogger | 排障无法串请求 | 网关透传 + MDC |

---

## P2｜错误契约与长期债

| ID | 风险 | 说明 | 建议方向 |
|----|------|------|----------|
| E1 | 无全局 `@ControllerAdvice` | DB/校验失败易变裸 500，与 `ApiResponse` 不一致 | 统一异常映射 + `@Valid` |
| E2 | HTTP 状态与业务 `code` 混用 | 业务多 200+code；网关鉴权 401 | 文档化契约；关键错误对齐 HTTP |
| E3 | `ApiResponse` 七服务各复制一份 | 长期易漂移 | 抽公共模块 |
| E4 | 注册/发帖等字段校验不足 | 空密码、超长 title 等 | Bean Validation |
| E5 | 服务间 URL 写死 | 扩容/迁移需改配置重发版 | 服务发现或配置中心 |
| E6 | schema 部分只在教程附录 | 部署易漏索引 | 每服务自带可执行 schema |

---

## 建议修复顺序

1. **安全基线**（S1–S9）：密钥外置、BCrypt、封旁路、拦 `/inner`、拆 admin、收紧 CORS、媒体校验  
2. **媒体与部署**（D1–D2）：对象存储、配置外置、完整编排  
3. **MQ 可靠**（M1–M3）：重试 + DLQ + Outbox/对账  
4. **Feed/HTTP 韧性**（R1–R4）：超时、熔断、批量接口、限流调参  
5. **可观测**（O1–O3）：真实 health、指标、TraceId  

网关侧已有正向设计（清 `X-User-Id`、路由级熔断、笔记 create/delete 的 afterCommit）应保留并补齐缺口。
