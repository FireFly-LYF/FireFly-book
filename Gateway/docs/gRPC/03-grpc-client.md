# ③ gRPC Client 与 RPC 类型（1~2 天）

> 所属：[gRPC 目录](./README.md) · 前置：[② gRPC 下游](./02-grpc-downstream.md)  
> 本模块产出：`cmd/grpc-client/main.go`

---

## 模块目标

编写命令行 gRPC Client，支持 **直连下游** 与 **经网关** 两种模式（网关模式在 ④ 完成后启用），理解连接、metadata、超时与 RPC 类型。

**结束时应达到**：

- `--target localhost:50052` 直连 SayHello 成功
- 能解释 Client 侧 `grpc.Dial` / `NewGreeterClient` / `SayHello` 调用链
- 知道如何在 metadata 中携带 JWT（为 ⑤ 鉴权做准备）

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | 实现直连 Client + flags | ⬜ |
| 第 2 天 | metadata、超时、四种 RPC 概念 | ⬜ |

---

## 第 1 天：直连 Client

### 任务

1. 新建 `cmd/grpc-client/main.go`
2. 支持 flags：`--target`、`--name`、`--token`（可选）
3. 打印 `message` 与 `instance`

### 概念

| 概念 | 含义 |
|------|------|
| `grpc.Dial` | 建立到 Server 的连接（HTTP/2） |
| `insecure.NewCredentials()` | 学习阶段不使用 TLS |
| `GreeterClient` | protoc 生成的 Client 接口 |
| `context.WithTimeout` | 单次 RPC 超时 |

### 参考代码 — `cmd/grpc-client/main.go`

```go
package main

import (
	"context"
	"flag"
	"log"
	"time"

	pb "go-proxy-demo/internal/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
)

func main() {
	target := flag.String("target", "localhost:50052", "direct upstream host:port")
	gateway := flag.String("gateway", "", "if set, dial gateway instead of target")
	name := flag.String("name", "world", "name in HelloRequest")
	token := flag.String("token", "", "optional Bearer JWT for metadata")
	timeout := flag.Duration("timeout", 5*time.Second, "rpc timeout")
	flag.Parse()

	addr := *target
	if *gateway != "" {
		addr = *gateway
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()

	if *token != "" {
		ctx = metadata.AppendToOutgoingContext(ctx, "authorization", "Bearer "+*token)
	}

	conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		log.Fatalf("dial %s: %v", addr, err)
	}
	defer conn.Close()

	client := pb.NewGreeterClient(conn)
	resp, err := client.SayHello(ctx, &pb.HelloRequest{Name: *name})
	if err != nil {
		log.Fatalf("SayHello: %v", err)
	}

	log.Printf("OK message=%q instance=%q (via %s)", resp.GetMessage(), resp.GetInstance(), addr)
}
```

### 验证 — 直连

```powershell
cd d:\A_Software\Java\SAVE\Gateway\gateway

# 确保 50052 在跑
go run ./cmd/grpc-client --target localhost:50052 --name Alice
# OK message="Hello Alice" instance="50052" (via localhost:50052)

go run ./cmd/grpc-client --target localhost:50053 --name Bob
```

### 验证 — 经网关（④ 完成后再测）

```powershell
go run ./cmd/grpc-client --gateway localhost:50051 --name Carol
```

### 检验标准

- [ ] 直连 50052/50053 均成功
- [ ] `--name` 生效
- [ ] 下游关闭时 Client 报错而非 hang（配合 timeout）

---

## 第 2 天：metadata 与 RPC 类型

### 任务

1. 理解 metadata 与 HTTP Header 的对应关系
2. 从 HTTP 网关获取 JWT，经 `--token` 传给 gRPC（⑤ 网关校验）
3. 阅读 streaming 概念（可不实现）

### metadata 与 JWT

HTTP 网关登录（已有）：

```powershell
curl.exe -X POST http://localhost:8080/gateway/login `
  -H "Content-Type: application/json" `
  -d "{\"tenant\":\"tenant-a\"}"
# 响应中的 token 复制给 grpc-client
```

带 Token 调用（Client 已支持）：

```powershell
go run ./cmd/grpc-client --gateway localhost:50051 --name test --token "<JWT>"
```

网关侧（⑤）从 **incoming metadata** 读取：

```go
md, ok := metadata.FromIncomingContext(ctx)
vals := md.Get("authorization") // "Bearer xxx"
```

注意：metadata key **小写**；`grpc-go` 会自动转为 lowercase。

### 四种 RPC 对照

| 类型 | proto 写法 | 典型场景 |
|------|------------|----------|
| Unary | `rpc M (A) returns (B)` | 普通 API |
| Server stream | `rpc M (A) returns (stream B)` | 订阅、搜索分页流 |
| Client stream | `rpc M (stream A) returns (B)` | 批量上传 |
| Bidi stream | `rpc M (stream A) returns (stream B)` | 聊天 |

透明代理（grpc-proxy）对四种类型均可转发，实现难度高于 HTTP ReverseProxy。

### context 最佳实践

```go
// 超时
ctx, cancel := context.WithTimeout(parent, 3*time.Second)
defer cancel()

// 取消（用户中断）
ctx, cancel := context.WithCancel(parent)
defer cancel()

// metadata 只影响当前 RPC
ctx = metadata.AppendToOutgoingContext(ctx, "x-request-id", "abc")
```

### 检验标准

- [ ] 能说明 metadata 类似 HTTP Header
- [ ] 知道 `--token` 对应 key `authorization`
- [ ] 能列举四种 RPC 类型（本项目中 SayHello 为 Unary）

---

## 使用 grpcurl 对照

| 操作 | grpcurl |
|------|---------|
| 列出服务 | `grpcurl -plaintext localhost:50052 list` |
| 列出方法 | `grpcurl -plaintext localhost:50052 list demo.Greeter` |
| 调用 | `grpcurl -plaintext -d '{"name":"x"}' localhost:50052 demo.Greeter/SayHello` |
| 带 metadata | `grpcurl -plaintext -H 'authorization: Bearer xxx' ...` |

---

## 模块结束标准

- [ ] `cmd/grpc-client` 可直连下游
- [ ] 支持 `--gateway`（④ 完成后验证）
- [ ] 支持 `--token` 写入 metadata
- [ ] 理解 Unary RPC 调用全流程

**下一步** → [04-grpc-proxy-gateway.md](./04-grpc-proxy-gateway.md)：实现透明代理网关（核心）。
