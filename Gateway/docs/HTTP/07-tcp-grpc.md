# ⑦ TCP / gRPC 代理（可选）

> 所属：[阶段 3 目录](./README.md) · 前置：[⑥ 中间件链](./06-middleware-chain.md) HTTP 网关已稳定  
> README：「HTTP/TCP/gRPC 三协议反向代理」——**时间紧可整章跳过**

---

## 模块目标

在 HTTP 网关之外，理解另外两种协议的透明转发：

| 协议 | 实现方式 | 难度 |
|------|----------|------|
| TCP | 标准库 `net`，`io.Copy` 双向 | ⭐⭐⭐ |
| gRPC | `grpc-proxy` 或自定义 stream | ⭐⭐⭐⭐ |

---

## 进度对照

| 部分 | 任务 | 预计 | 状态 |
|------|------|------|------|
| TCP 第 1 天 | 理解 TCP vs HTTP 代理差异 | 1 天 | ⬜ |
| TCP 第 2~3 天 | 实现端口转发 | 2 天 | ⬜ |
| TCP 第 4~5 天 | 多下游 + 简单 LB（可选） | 2 天 | ⬜ |
| gRPC 第 1 周 | grpc 基础 + 单向 RPC | 1 周 | ⬜ |
| gRPC 第 2 周 | grpc-proxy 透明转发 | 1 周 | ⬜ |

---

## TCP 第 1 天：概念

### HTTP vs TCP 代理

| | HTTP 代理 | TCP 代理 |
|---|-----------|----------|
| 感知内容 | 能读 Header、Path | 字节流，不解析应用层 |
| 实现 | ReverseProxy | `net.Listen` + `net.Dial` + `io.Copy` |
| 端口 | 8080 → 9001 | 例如 8081 → 9001 |
| 中间件 | JWT、路径重写 | 通常只能做连接级 LB |

### 适用场景

- MySQL、Redis、自定义二进制协议
- WebSocket 底层（HTTP 升级前可先 TCP）

### 检验标准

- [ ] 能说出 TCP 代理「看不到 HTTP Path」
- [ ] 知道 README TCP 用标准库 `net`

---

## TCP 第 2~3 天：透明转发

### 任务

1. 新建 `cmd/tcp-gateway/main.go`
2. 监听 `:8081`，每个连接 dial 到 `:9001`
3. 两个 goroutine 分别 `io.Copy` 双向转发

### 参考代码

```go
package main

import (
    "io"
    "log"
    "net"
)

func main() {
    ln, err := net.Listen("tcp", ":8081")
    if err != nil {
        log.Fatal(err)
    }
    log.Println("tcp gateway :8081 -> :9001")
    for {
        client, err := ln.Accept()
        if err != nil {
            log.Println("accept:", err)
            continue
        }
        go handle(client, "localhost:9001")
    }
}

func handle(client net.Conn, target string) {
    defer client.Close()
    upstream, err := net.Dial("tcp", target)
    if err != nil {
        log.Println("dial:", err)
        return
    }
    defer upstream.Close()

    done := make(chan struct{}, 2)
    go copyAndClose(done, client, upstream)
    go copyAndClose(done, upstream, client)
    <-done
}

func copyAndClose(done chan struct{}, dst, src net.Conn) {
    _, _ = io.Copy(dst, src)
    done <- struct{}{}
}
```

### 测试用 echo 服务（可选）

```go
// cmd/downstream_tcp/main.go — 监听 9010，回显收到的字节
```

或用 `nc`：

```powershell
# 终端 1：下游 nc
nc -l -p 9001

# 终端 2：tcp gateway
go run ./cmd/tcp-gateway

# 终端 3：客户端连网关
nc localhost 8081
```

### 检验标准

- [ ] 经 8081 发送的文字出现在 9001
- [ ] 9001 回复能回到客户端
- [ ] 理解 `io.Copy` 阻塞直到连接关闭

---

## TCP 第 4~5 天：TCP 负载均衡（可选）

### 思路

- Accept 连接后，按轮询选 `9001/9002/9003` 再 Dial
- 无 HTTP 健康检查，可用 TCP dial 探测端口是否 open

### 检验标准

- [ ] 多个 TCP 下游可轮换
- [ ] 某端口不通时跳过

---

## gRPC 详细教程

gRPC 部分已展开为独立目录，含逐日任务、完整代码与自测清单：

**→ [docs/gRPC/README.md](../gRPC/README.md)**

下文保留速览；实现请按 gRPC 目录 ①~⑥ 顺序进行。

---

## gRPC 第 1 周：基础

### 任务

1. 安装 `protoc`、Go grpc 插件
2. 写最小 `.proto` + server + client
3. 理解 unary RPC vs streaming

### 最小 proto 示例

```protobuf
syntax = "proto3";
package demo;
option go_package = "./pb";

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest { string name = 1; }
message HelloReply   { string message = 1; }
```

### 命令

```powershell
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

protoc --go_out=. --go-grpc_out=. demo.proto
```

### 检验标准

- [ ] client 直连 server 能调通 SayHello
- [ ] 知道 gRPC 基于 HTTP/2

---

## gRPC 第 2 周：grpc-proxy

### 任务

1. 了解 [mwitkow/grpc-proxy](https://github.com/mwitkow/grpc-proxy) 或官方 transparent handler
2. 网关作为 `:50051` 前端，转发到后端 `:50052`
3. （进阶）结合 metadata 做鉴权

### 概念

| 点 | 说明 |
|----|------|
| **透明代理** | 不解 proto，按 method 名转发 |
| **metadata** | 类似 HTTP Header，可传 JWT |
| **难度** | 比 HTTP ReverseProxy 高，错误处理复杂 |

### 参考方向（伪代码）

```go
director := func(ctx context.Context, fullMethodName string) (context.Context, *grpc.ClientConn, error) {
    // 选后端 conn，类似 lb.Next()
    return ctx, backendConn, nil
}
server := grpc.NewServer(grpc.UnknownServiceHandler(grpcproxy.TransparentHandler(director)))
```

### 检验标准

- [ ] 知道 gRPC 代理与 HTTP 代理架构相似（前端 server + 后端 client）
- [ ] 可选：跑通一个 method 经网关转发
- [ ] 评估：时间不够可仅读文档，阶段 4 再补

---

## 模块结束标准（可选）

- [ ] TCP `:8081` → `:9001` 双向转发可用
- [ ] 能对比 HTTP / TCP / gRPC 三种协议在网关层的差异
- [ ] （加分）gRPC 至少一个 RPC 经代理成功

**跳过本章不影响阶段 3 HTTP 毕业 → 直接进入 [阶段 4](../learning-roadmap.md#阶段-4工程化可选项目收尾)**

---

## 推荐资源

| 资源 | 链接 |
|------|------|
| Go net 包 | https://pkg.go.dev/net |
| gRPC Go 快速开始 | https://grpc.io/docs/languages/go/quickstart/ |
| grpc-proxy | https://github.com/mwitkow/grpc-proxy |

---

## 常见问题

### Q：HTTP 网关做好了还要做 TCP 吗？

毕业设计 / README 完整版建议做；练手 MVP 可跳过。

### Q：三种协议共用一个端口吗？

生产常用不同端口或 SNI/ALPN；学习阶段分开端口最简单。

### Q：JWT 怎么用在 gRPC？

放在 metadata：`authorization` key，网关 director 里校验。
