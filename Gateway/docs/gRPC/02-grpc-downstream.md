# ② gRPC 下游 Server（2~3 天）

> 所属：[gRPC 目录](./README.md) · 前置：[① 环境与 proto](./01-environment-and-proto.md)  
> 本模块产出：`cmd/grpc-downstream/main.go` + `internal/grpcserver/`（可选）

---

## 模块目标

实现可启动多个实例的 gRPC 下游服务，响应中带 `instance` 字段（对应端口），便于后续验证负载均衡。

```
Client --直连--> :50052  SayHello → "Hello world (instance=50052)"
Client --直连--> :50053  SayHello → "Hello world (instance=50053)"
```

**结束时应达到**：两个终端分别跑 50052、50053，直连均能返回正确 message。

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | 实现 GreeterServer 业务逻辑 | ⬜ |
| 第 2 天 | `cmd/grpc-downstream` + `--port` | ⬜ |
| 第 3 天 | 双实例启动 + grpcurl / 临时 client 验证 | ⬜ |

---

## 第 1 天：实现 Server

### 任务

1. 新建 `internal/grpcserver/greeter.go`，实现 `pb.GreeterServer`
2. 理解 `context.Context` 在 gRPC 中的作用（超时、metadata）

### 概念

| 概念 | 含义 |
|------|------|
| `RegisterGreeterServer` | 把实现注册到 `grpc.Server` |
| `UnimplementedGreeterServer` | 嵌入后可只实现部分 RPC，向前兼容 |
| `grpc.NewServer()` | 创建服务端，可挂 interceptor |
| 监听 | `net.Listen("tcp", ":50052")` + `grpcServer.Serve(lis)` |

### 参考代码 — `internal/grpcserver/greeter.go`

```go
package grpcserver

import (
	"context"
	"fmt"

	pb "go-proxy-demo/internal/pb"
)

// Instance 由 main 启动时设置，类似 HTTP downstream 的 handler.Instance
var Instance = "50052"

type Greeter struct {
	pb.UnimplementedGreeterServer
}

func (g *Greeter) SayHello(ctx context.Context, req *pb.HelloRequest) (*pb.HelloReply, error) {
	name := req.GetName()
	if name == "" {
		name = "world"
	}
	return &pb.HelloReply{
		Message:  fmt.Sprintf("Hello %s", name),
		Instance: Instance,
	}, nil
}
```

### 错误处理约定

| gRPC 错误 | 含义 | HTTP 近似 |
|-----------|------|-----------|
| `codes.OK` | 成功 | 200 |
| `codes.InvalidArgument` | 参数错误 | 400 |
| `codes.Unauthenticated` | 未鉴权 | 401 |
| `codes.Unavailable` | 服务不可用 | 503 |

示例（参数校验）：

```go
import "google.golang.org/grpc/codes"
import "google.golang.org/grpc/status"

if req.GetName() == "" {
    return nil, status.Error(codes.InvalidArgument, "name required")
}
```

### 检验标准

- [ ] `Greeter` 嵌入 `UnimplementedGreeterServer`
- [ ] `SayHello` 返回 `message` 与 `instance`

---

## 第 2 天：启动入口

### 任务

1. 新建 `cmd/grpc-downstream/main.go`
2. 支持 `--port` flag，默认 `50052`
3. 启动时打印监听地址

### 参考代码 — `cmd/grpc-downstream/main.go`

```go
package main

import (
	"flag"
	"log"
	"net"

	"go-proxy-demo/internal/grpcserver"

	pb "go-proxy-demo/internal/pb"

	"google.golang.org/grpc"
)

func main() {
	port := flag.String("port", "50052", "listen port")
	flag.Parse()

	grpcserver.Instance = *port

	lis, err := net.Listen("tcp", ":"+*port)
	if err != nil {
		log.Fatalf("listen: %v", err)
	}

	s := grpc.NewServer()
	pb.RegisterGreeterServer(s, &grpcserver.Greeter{})

	log.Printf("grpc downstream listening on :%s (instance=%s)", *port, *port)
	if err := s.Serve(lis); err != nil {
		log.Fatalf("serve: %v", err)
	}
}
```

### 与 HTTP downstream 对照

| HTTP `cmd/downstream` | gRPC `cmd/grpc-downstream` |
|-----------------------|----------------------------|
| `gin.Default()` | `grpc.NewServer()` |
| `r.GET("/user", ...)` | `RegisterGreeterServer` |
| `r.Run(":9001")` | `Serve(lis)` |
| `handler.Instance` | `grpcserver.Instance` |

### 检验标准

- [ ] `go run ./cmd/grpc-downstream` 默认监听 50052
- [ ] `go run ./cmd/grpc-downstream --port 50053` 可换端口

---

## 第 3 天：双实例与验证

### 任务

1. 开两个终端跑 50052、50053
2. 用 grpcurl 或临时 Client 直连验证
3. 确认响应 `instance` 与端口一致

### 启动命令

```powershell
cd d:\A_Software\Java\SAVE\Gateway\gateway

# 终端 1
go run ./cmd/grpc-downstream --port 50052

# 终端 2
go run ./cmd/grpc-downstream --port 50053
```

### 方式 A — grpcurl（推荐调试）

安装：

```powershell
# scoop install grpcurl
# 或 go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

调用（**需要反射**：见下方「开启反射」）：

```powershell
grpcurl -plaintext -d "{\"name\":\"Alice\"}" localhost:50052 demo.Greeter/SayHello
grpcurl -plaintext -d "{\"name\":\"Bob\"}" localhost:50053 demo.Greeter/SayHello
```

期望 JSON 响应含 `"instance": "50052"` 等。

#### 开启 gRPC 反射（可选，仅开发）

在 `main.go` 的 `Serve` 前加入：

```go
import "google.golang.org/grpc/reflection"

reflection.Register(s)
```

生产环境通常关闭反射，改用 proto 文件给 grpcurl：

```powershell
grpcurl -plaintext -proto api/proto/greeter.proto -d "{\"name\":\"Alice\"}" localhost:50052 demo.Greeter/SayHello
```

### 方式 B — 最小 Go Client（下一章正式写）

临时在 `main_test.go` 或 scratch 文件：

```go
conn, _ := grpc.Dial("localhost:50052", grpc.WithTransportCredentials(insecure.NewCredentials()))
defer conn.Close()
client := pb.NewGreeterClient(conn)
resp, err := client.SayHello(context.Background(), &pb.HelloRequest{Name: "test"})
// resp.Message, resp.Instance
```

需 `google.golang.org/grpc/credentials/insecure`（Go 1.22+ 推荐 insecure 包）。

### 检验标准

- [ ] 50052、50053 同时运行无端口冲突
- [ ] 直连 50052 返回 `instance=50052`
- [ ] 直连 50053 返回 `instance=50053`
- [ ] 下游未启动时 Client 得到 `Unavailable` 类错误

---

## 进阶（可选）

### Server 端 interceptor — 日志

```go
func loggingUnary(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
    log.Printf("[grpc] %s", info.FullMethod)
    return handler(ctx, req)
}

s := grpc.NewServer(grpc.UnaryInterceptor(loggingUnary))
```

### 健康检查服务

后续网关健康探测可注册标准 health：

```go
import "google.golang.org/grpc/health"
import healthpb "google.golang.org/grpc/health/grpc_health_v1"

healthServer := health.NewServer()
healthpb.RegisterHealthServer(s, healthServer)
healthServer.SetServingStatus("demo.Greeter", healthpb.HealthCheckResponse_SERVING)
```

---

## 模块结束标准

- [ ] `internal/grpcserver` 实现 SayHello
- [ ] `cmd/grpc-downstream` 支持 `--port`
- [ ] 至少 2 个实例可同时运行并被直连调用

**下一步** → [03-grpc-client.md](./03-grpc-client.md)：编写正式测试 Client。
