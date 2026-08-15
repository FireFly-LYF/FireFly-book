# FireFly-book 后端上线风险清单

> 基于当前仓库实码审查。整体适合教程/联调，**直接公网暴露风险高**。  
> 优先级：P0 上线必拦 → P1 流量上来会痛 → P2 长期债。  
> **状态**：`未改` / `已缓解`（主路径已挡，仍有残留） / `已修复`（本项目标已达成）。

---

## P0｜身份与内网边界

| ID | 状态 | 风险（原问题） | 证据位置 | 当前结论 | 仍待做（若有） |
|----|------|----------------|----------|----------|----------------|
| S1 | **已修复** | Java 只信 `X-User-Id`、旁路可冒充 | 网关 `proxy/http.go`；`firefly-internal-auth`；各服务 `server.address` | ① 业务口仅绑 `127.0.0.1`（外网不可直连）② 网关注入并签名 `X-Gateway-Ts`/`X-Gateway-Sign`，下游校验（含时钟窗）③ 伪造裸 `X-User-Id` 会被 401 | 生产换 Secret Manager + 时钟同步（密钥外置见 **S3**） |
| S2 | **已缓解** | `/api/*/inner/**` 经网关对登录用户开放 | 网关 `BlockList`：`/api/search/inner`、`/api/notify/inner`、`/api/<svc>/inner/**` | 经网关访问 inner → **403**；旁路直连须过 S1 的 HMAC，不能再靠瞎填用户头 | 持有 `hmac-secret` 的同机调用仍可打 inner；生产可再加服务账号 / 仅 MQ |
| S3 | **已修复** | JWT / DB / MQ / HMAC / admin 密钥硬编码进仓库 | 各 `application.yml`、`gateway.yaml` 现为 `${ENV}`；`.env` + `start-all.ps1` 注入 | 配置无默认密钥；缺环境变量则启动失败 | 生产改用 Secret Manager；勿把 `.env` 提交进仓 |
| S4 | **已修复** | 密码 MD5 无盐 | `UserService` + `spring-security-crypto` | 新注册走 BCrypt；旧 MD5 登录成功后自动升级写入 BCrypt | 无；可选后续去掉 MD5 兼容分支 |
| S5 | **已修复** | 旧 `/gateway/login` 无真实凭证即可拿与业务同 secret 的 JWT | 现为运维口令登录；签发密钥为 `admin.jwt_secret` | 不能再「只填租户」拿业务级 Token | 口令/密钥外置见 **S3** |
| S6 | **已修复** | 业务用户 JWT 可访问网关运维 API | `AdminJWTAuth(admin.jwt_secret)` + `typ=admin`；与 `jwt.secret` 强制不同 | 业务 Access 验签失败 → 401；运维须 `/gateway/login` + `admin.password` | 密钥外置（S3）；生产可再加 IP 限制 / mTLS |
| S7 | **已修复** | CORS 反射任意 Origin | `cors.go` + `gateway.yaml` `cors.allowed_origins` | 仅白名单 Origin 可跨域；未命中不写 Allow-Origin，预检 403 | 生产把名单换成正式前端域名；空名单=最严 |
| S8 | **已修复** | `/files/**` 无强制鉴权 + 本地静态挂载 | `SignedFileFilter` + `POST /api/media/sign`；前端 `SignedImg` | 访问须带未过期 `exp`+`sig`；规范路径入库，展示时再签名 | 远期可迁对象存储预签名 |
| S9 | **已修复** | 媒体仅靠 `Content-Type` 前缀校验 | `ImageProbe` + `MediaService.save` | 魔数识别 JPEG/PNG/GIF/WEBP；扩展名白名单；落盘名=UUID+检测扩展名 | 可选 ImageIO 二次解码/重编码 |

---

## P0｜MQ 与最终一致性

| ID | 状态 | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|------|----------|----------|----------|
| M1 | **已修复** | 消费者吞异常后 ACK → 消息永久丢失 | `SearchEventListener` / `NotifyEventListener` 失败上抛 | 瞬时失败走 listener retry；耗尽进 DLQ | 监控 DLQ；毒消息人工处理 |
| M2 | **已修复** | 无 DLQ / 无重试退避配置 | 各队列挂 `firefly.dlx` + `*.dlq`；`retry.max-attempts=3` | 重试 3 次退避后进 DLQ，避免无限重投 | 本机旧队列需先删除再启动（参数变更） |
| M3 | **已修复（笔记）** | 生产者发送失败只 warn，主流程仍成功 | content `outbox_event` + `OutboxRelay`；`NoteService` 事务内入箱 | 笔记事件先落 Outbox 再投递；Rabbit 故障可重试，不再静默丢 | user/social 通知 Outbox 可同样铺开；监控 `DEAD` |

---

## P0｜部署与媒体配置

| ID | 状态 | 风险（原问题） | 证据位置 | 当前结论 | 仍待做（若有） |
|----|------|----------------|----------|----------|----------------|
| D1 | **已缓解** | 全本地硬编码：`127.0.0.1`、Windows 路径、固定端口 | 各 `application.yml` 现为 `${ENV:本机默认}`；`MEDIA_STORAGE_DIR` / `${user.home}/FireFlyData/media` | 本机仍可开箱；容器/生产用环境变量覆盖；媒体目录可挂卷 | 远期对象存储预签名（与 S8 一致） |
| D2 | **已缓解** | 无生产 profile；Docker 未编排 Java 业务服务 | 各服务 `application-prod.yml`；`deploy/docker-compose.yml` + `gateway-compose.yaml` | `prod` 绑 `0.0.0.0`、依赖走服务名；compose 编排 infra+Java+网关，媒体卷 `media-data` | K8s / Secret Manager；表结构需另灌 schema |

---

## P1｜韧性与性能

| ID | 状态 | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|------|----------|----------|----------|
| R1 | 未改 | Feed 读扩散：关注数 × HTTP | `FeedService.followingFeed` → `contentClient.listByUser` | 关注多则延迟线性恶化，易拖垮 | 批量拉笔记 / 写扩散时间线；限制扫描上限 |
| R2 | 未改 | Java 侧 `RestTemplate` 裸建、无超时/熔断 | `HttpConfig` / `AppConfig` 等 `new RestTemplate()` | 下游挂起占满线程池，雪崩 | 连接/读超时；Resilience4j |
| R3 | 未改 | 网关 ReverseProxy 无显式超时 | `proxy/http.go` | 慢上游占满代理连接 | Transport Timeout + 与熔断联动 |
| R4 | 未改 | 限流阈值极大（形同关闭） | `gateway.yaml` `rate: 100000` | 登录/上传/搜索易被刷 | 按路由/用户/IP 分级限流 |
| R5 | 未改 | ES 搜索带 wildcard `*q*` | `ElasticsearchRestClient.search` | 大数据量下 CPU/延迟差，慢查询打挂 | IK 分词；限 q 长度；禁前导通配 |
| R6 | 未改 | 无 Hikari 显式调优；业务缓存几乎空白 | 各 datasource 仅基础配置 | 流量上涨时连接池/热点不足 | 按实例调池；热点用户/笔记缓存 |

---

## P1｜数据一致性与幂等

| ID | 状态 | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|------|----------|----------|----------|
| C1 | 未改 | 笔记 update 未与 create 对齐事务 + afterCommit | `NoteService.update` | 更新失败仍可能已发 MQ，或边界不清 | 与 create 对齐 `@Transactional` + afterCommit |
| C2 | 未改 | 点赞「先查后插」竞态 | `LikeService.like`；表有 `uk_like` | 并发双击可能 500 而非业务友好错误 | 捕获 DuplicateKey / INSERT IGNORE |
| C3 | 未改 | 通知表无幂等键 | `NotifyService.create` 每次 insert | MQ 重投产生重复 LIKE/FOLLOW 通知 | `(type, from_user_id, ref_id)` 唯一或消费去重 |
| C4 | 未改 | 关键写操作无 `Idempotency-Key` | 发帖/评论等 | 客户端重试导致重复内容 | 幂等表或唯一业务键 |

---

## P1｜可观测性

| ID | 状态 | 风险 | 证据位置 | 上线后果 | 建议方向 |
|----|------|------|----------|----------|----------|
| O1 | 未改 | 无 Actuator / Micrometer / Tracing | 各服务 `pom.xml` | 难定位延迟、错误率、依赖健康 | `/actuator/health` + 指标 + TraceId 贯穿 |
| O2 | 未改 | Health 仅为字符串 `"OK"` | 各 `HealthController` | 网关认为健康，但 DB/MQ 已挂 | 探真实依赖；区分 liveness/readiness |
| O3 | 未改 | Java 侧无统一结构化字段（userId、requestId） | 对比网关已有 RequestLogger | 排障无法串请求 | 网关透传 + MDC |

---

## P2｜错误契约与长期债

| ID | 状态 | 风险 | 说明 | 建议方向 |
|----|------|------|------|----------|
| E1 | 未改 | 无全局 `@ControllerAdvice` | DB/校验失败易变裸 500，与 `ApiResponse` 不一致 | 统一异常映射 + `@Valid` |
| E2 | 未改 | HTTP 状态与业务 `code` 混用 | 业务多 200+code；网关鉴权 401 | 文档化契约；关键错误对齐 HTTP |
| E3 | 未改 | `ApiResponse` 七服务各复制一份 | 长期易漂移 | 抽公共模块（可参考 `firefly-internal-auth`） |
| E4 | 未改 | 注册/发帖等字段校验不足 | 空密码、超长 title 等 | Bean Validation |
| E5 | 未改 | 服务间 URL 写死 | 扩容/迁移需改配置重发版 | 服务发现或配置中心 |
| E6 | 未改 | schema 部分只在教程附录 | 部署易漏索引 | 每服务自带可执行 schema |

---

## 建议修复顺序

1. **安全基线剩余**：无（S1–S9 主路径已收口；生产密钥改 Secret Manager）  
2. **媒体与部署剩余**（D1–D2）：对象存储；K8s/Secret Manager；每服务可执行 schema  
3. **MQ 可靠剩余**：user/social 通知侧 Outbox；监控 DEAD  
4. **Feed/HTTP 韧性**（R1–R4）：超时、熔断、批量接口、限流调参  
5. **可观测**（O1–O3）：真实 health、指标、TraceId  

### 已处理（勿再当未改项排期）

| ID | 做了什么 |
|----|----------|
| **S1** | `127.0.0.1` 绑定 + 网关 HMAC 身份头 + `firefly-internal-auth` 统一校验 |
| **S2** | 网关拦截 `/inner/**`（403）；旁路身份伪造由 S1 收口 |
| **S3** | `application.yml`/`gateway.yaml` 密钥改 `${ENV}`；`start-all.ps1` 从 `.env` 注入且禁止空值 |
| **S4** | 注册/登录改 BCrypt；存量 MD5 登录成功后自动升级 |
| **S5** | `/gateway/login` 改为运维口令；不再签发业务密钥 JWT |
| **S6** | 运维 API 使用独立 `admin.jwt_secret` + `typ=admin`，业务 Token 不可用 |
| **S7** | CORS 改为 Origin 白名单 |
| **S8** | `/files/**` 签名 URL（exp+sig）；无签名 403 |
| **S9** | 上传魔数 + 扩展名白名单 + 服务端决定扩展名 |
| **M1** | Search/Notify 消费失败上抛，不再吞异常 ACK |
| **M2** | 业务队列 DLX + DLQ；listener 重试 3 次退避 |
| **M3** | content 笔记 Transactional Outbox（事务内入箱 + Relay 投递） |
| **D1** | 主机/库/MQ/媒体路径改 `${ENV:默认}`；媒体默认 `${user.home}/FireFlyData/media`，compose 挂 `/data/media` |
| **D2** | 各服务 `application-prod.yml`；`deploy/docker-compose.yml` 编排 Java+infra+Gateway（`gateway-compose.yaml`） |

网关侧既有正向设计（清客户端 `X-User-Id` 再注入、路由级熔断、笔记 create/delete 的 afterCommit）继续保留。
