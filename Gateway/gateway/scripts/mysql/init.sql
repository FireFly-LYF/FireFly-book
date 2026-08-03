-- =============================================================================
-- Gateway MySQL 初始化脚本
-- =============================================================================
--
-- 作用：创建 gateway 数据库、两张表（upstreams / tenants），并写入初始数据。
--
-- 可反复执行（幂等）：
--   - CREATE ... IF NOT EXISTS  → 表已存在则跳过
--   - INSERT IGNORE             → 主键重复则跳过，不报错
--
-- 修改数据：直接编辑本文件，保存后执行：
--   .\scripts\mysql\apply.ps1 -Container mysql -Password 123456
--
-- 与 Go 代码的对应关系：
--   upstreams 表  ↔  registry/mysql.go 的 Register / List / Deregister
--   tenants  表   ↔  租户白名单（后续可接 tenant 包从 DB 加载）
--   id 字段       ↔  upstream.Endpoint.ID（必须与 Go 生成规则一致）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 创建数据库
-- -----------------------------------------------------------------------------
-- gateway：本项目专用库名，与 gateway.yaml 中 dsn 的 /gateway 对应
-- utf8mb4：支持中文及 emoji，MySQL 推荐字符集
CREATE DATABASE IF NOT EXISTS gateway DEFAULT CHARSET utf8mb4;

-- 切换到 gateway 库，后续语句都在此库执行
USE gateway;

-- -----------------------------------------------------------------------------
-- 2. 下游服务表 upstreams
-- -----------------------------------------------------------------------------
-- 每一行 = 一个下游节点（HTTP / gRPC / TCP 可只填其一或组合）
-- 网关启动时 loadAll() 全量 SELECT 进内存 map；Register API 会 INSERT 新行
CREATE TABLE IF NOT EXISTS upstreams (
  -- 主键，与 Go 中 upstream.Endpoint.ID 完全一致，例如：
  --   HTTP only → http://localhost:9001
  --   gRPC only → grpc:localhost:50052
  --   TCP only  → tcp:localhost:9010
  id         VARCHAR(256) PRIMARY KEY,

  -- HTTP 下游 base URL（含 http://），代理转发时用
  http_url   VARCHAR(512) DEFAULT NULL,

  -- gRPC 下游地址（host:port，无协议前缀）
  grpc_addr  VARCHAR(128) DEFAULT NULL,

  -- TCP 下游地址（host:port）
  tcp_addr   VARCHAR(128) DEFAULT NULL,

  -- 负载均衡权重，weighted / consistent_hash 策略使用；默认 1
  weight     INT NOT NULL DEFAULT 1,

  -- 健康状态：1=健康 0=不健康；健康检查主要更新内存，可不写回 DB
  healthy    TINYINT(1) NOT NULL DEFAULT 1,

  -- 最近一次健康检查时间；NULL 表示尚未检查
  last_check TIMESTAMP NULL DEFAULT NULL,

  -- 记录创建时间，插入时自动填充
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  -- 记录更新时间，任意字段变更时自动刷新
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 3. 租户表 tenants
-- -----------------------------------------------------------------------------
-- 每一行 = 一个可登录租户；JWT 的 iss 字段 = id
-- 当前登录仍读 gateway.yaml 的 tenants，此表为后续 Admin 动态管理预留
CREATE TABLE IF NOT EXISTS tenants (
  -- 租户 ID，与 JWT iss、login 请求中的 tenant 字段一致
  id         VARCHAR(64) PRIMARY KEY,

  -- 展示名称（可与 id 相同）
  name       VARCHAR(128) NOT NULL,

  -- 1=启用（可登录） 0=禁用
  status     TINYINT NOT NULL DEFAULT 1,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 4. 初始租户数据
-- -----------------------------------------------------------------------------
-- INSERT IGNORE：id 已存在则跳过，可反复执行 apply.ps1
-- 与 gateway.yaml 中 tenants: [tenant-a, tenant-b] 保持一致
INSERT IGNORE INTO tenants (id, name) VALUES
  ('tenant-a', 'tenant-a'),
  ('tenant-b', 'tenant-b');

-- -----------------------------------------------------------------------------
-- 5. 初始下游节点 — HTTP
-- -----------------------------------------------------------------------------
-- id 与 http_url 相同（纯 HTTP 节点的 ID 规则见 upstream/idFromEndpoint）
-- weight 对应 yaml loadbalancer.upstreams[].weight
INSERT IGNORE INTO upstreams (id, http_url, weight) VALUES
  ('http://localhost:9001', 'http://localhost:9001', 1),
  ('http://localhost:9002', 'http://localhost:9002', 2),
  ('http://localhost:9003', 'http://localhost:9003', 3);

-- -----------------------------------------------------------------------------
-- 6. 初始下游节点 — gRPC
-- -----------------------------------------------------------------------------
-- 纯 gRPC 节点 id 格式：grpc:<host:port>（Go 代码 idFromEndpoint 生成）
INSERT IGNORE INTO upstreams (id, grpc_addr, weight) VALUES
  ('grpc:localhost:50052', 'localhost:50052', 1),
  ('grpc:localhost:50053', 'localhost:50053', 2);

-- -----------------------------------------------------------------------------
-- 7. 初始下游节点 — TCP
-- -----------------------------------------------------------------------------
-- 纯 TCP 节点 id 格式：tcp:<host:port>
INSERT IGNORE INTO upstreams (id, tcp_addr, weight) VALUES
  ('tcp:localhost:9010', 'localhost:9010', 1);
