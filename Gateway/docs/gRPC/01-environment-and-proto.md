# ① 环境与 Protocol Buffers（2~3 天）

> 所属：[gRPC 目录](./README.md)  
> 前置：HTTP 网关阶段 3 ①~⑥ 已完成或可并行  
> 本模块产出：`api/proto/greeter.proto` + `internal/pb/` 生成代码

---

## 模块目标

完成 gRPC 开发工具链安装，编写服务定义文件（`.proto`），生成 Go 桩代码，为下游 Server 与透明代理打基础。

**结束时应达到**：

- 本机可执行 `protoc` 及 Go 插件
- 仓库内有可编译的 `internal/pb/*.pb.go`
- 能解释 proto3 中 `service` / `message` / `rpc` 的含义

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | 理解 gRPC 与 HTTP/2、RPC 类型 | ⬜ |
| 第 2 天 | 安装 protoc + 插件，写 greeter.proto | ⬜ |
| 第 3 天 | 生成代码、go mod、编译通过 | ⬜ |

---

## 第 1 天：gRPC 核心概念

### 任务

1. 阅读 [gRPC Core Concepts](https://grpc.io/docs/what-is-grpc/core-concepts/)
2. 对比 HTTP REST 与 gRPC 的差异
3. 理解四种 RPC 类型（本阶段以 **Unary** 为主）

### 概念

| 概念 | 含义 |
|------|------|
| **IDL** | Interface Definition Language，即 `.proto` 文件，约定服务与方法 |
| **Stub** | Client 侧生成的调用代码（`GreeterClient`） |
| **Server** | 实现 `.proto` 中 `service` 的 Go struct |
| **Unary RPC** | 一问一答：一个请求 → 一个响应（类似 HTTP POST） |
| **Server streaming** | 一个请求 → 多个响应（如推送） |
| **Client streaming** | 多个请求 → 一个响应（如上传） |
| **Bidirectional streaming** | 双向流 |
| **metadata** | 键值对，类似 HTTP Header，常用于 JWT、trace-id |
| **full method name** | 如 `/demo.Greeter/SayHello`，代理转发时的路由键 |

### HTTP vs gRPC（网关视角）

| | HTTP（现有） | gRPC |
|---|-------------|------|
| 传输 | HTTP/1.1 为主 | **HTTP/2**（多路复用、二进制帧） |
| 契约 | OpenAPI / 约定 | **`.proto` 强类型** |
| 路由键 | Path `/api/user` | Method `/package.Service/Method` |
| 代理 | ReverseProxy 改 Path | grpc-proxy 按 method **透明转发** |
| 鉴权 | Header | metadata |

### 检验标准

- [ ] 能说出 gRPC 基于 HTTP/2
- [ ] 能区分 Unary 与 Streaming（本项目中先实现 Unary）
- [ ] 知道「透明代理」不需要在网关侧 import 业务 proto 也能转发（靠 method 名）

---

## 第 2 天：安装工具链

### 任务

1. 安装 `protoc` 编译器
2. 安装 Go 代码生成插件
3. 创建 `api/proto/greeter.proto`

### Windows 安装 protoc

**方式 A — Scoop（推荐）**

```powershell
scoop install protobuf
protoc --version
# libprotoc 3.x 或 25.x 均可
```

**方式 B — 手动**

1. 打开 https://github.com/protocolbuffers/protobuf/releases
2. 下载 `protoc-*-win64.zip`
3. 解压，将 `bin/protoc.exe` 加入 PATH

**方式 C — Chocolatey**

```powershell
choco install protoc
```

### 安装 Go 插件

```powershell
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
```

确保 `$GOPATH/bin` 或 `$HOME/go/bin` 在 PATH 中：

```powershell
# PowerShell 临时加入（按实际 GOPATH 调整）
$env:PATH += ";$env:USERPROFILE\go\bin"
protoc-gen-go --version
protoc-gen-go-grpc --version
```

### 添加 Go 模块依赖

在 `gateway/` 目录：

```powershell
cd d:\A_Software\Java\SAVE\Gateway\gateway

go get google.golang.org/grpc@latest
go get google.golang.org/protobuf@latest
```

后续做 grpc-proxy 网关时还需要：

```powershell
go get github.com/mwitkow/grpc-proxy/proxy@latest
```

### 创建 greeter.proto

路径：`gateway/api/proto/greeter.proto`

```protobuf
syntax = "proto3";

// 逻辑包名，影响生成的 Go 包路径与 full method 前缀
package demo;

// 必须与 go.mod module 一致：go-proxy-demo/internal/pb
option go_package = "go-proxy-demo/internal/pb;pb";

// Greeter 演示服务：与 HTTP 下游 /user 类似，用于验证 LB
service Greeter {
  // Unary RPC：一个 HelloRequest → 一个 HelloReply
  rpc SayHello (HelloRequest) returns (HelloReply);

  // 可选：Server streaming，进阶时在 downstream 实现
  // rpc SayHelloStream (HelloRequest) returns (stream HelloReply);
}

message HelloRequest {
  string name = 1; // 字段编号 1，不可随意改（兼容性）
}

message HelloReply {
  string message = 1;
  string instance = 2; // 下游端口标识，便于观察 LB，类似 HTTP 的 instance 字段
}
```

### proto 编写要点

| 规则 | 说明 |
|------|------|
| `syntax = "proto3"` | 不要用 proto2 语法 |
| 字段编号 | 1~15 编码更省空间；**已发布后不要改号** |
| `go_package` | 格式：`导入路径;包名`，与 `go.mod` module 对齐 |
| `package demo` | full method 为 `/demo.Greeter/SayHello` |
| 注释 | `//` 单行注释会进入生成代码文档 |

### 检验标准

- [ ] `protoc --version` 有输出
- [ ] `protoc-gen-go`、`protoc-gen-go-grpc` 在 PATH 中
- [ ] `greeter.proto` 已创建且无语法错误

---

## 第 3 天：生成 Go 代码

### 任务

1. 执行 `protoc` 生成 `internal/pb/`
2. `go build ./...` 确认编译
3. 阅读生成文件，找到 `GreeterServer` / `GreeterClient` 接口

### 生成命令

在 `gateway/` 根目录执行：

```powershell
cd d:\A_Software\Java\SAVE\Gateway\gateway

# 若 internal/pb 不存在则创建
New-Item -ItemType Directory -Force -Path internal/pb | Out-Null

protoc `
  --proto_path=api/proto `
  --go_out=internal/pb --go_opt=paths=source_relative `
  --go-grpc_out=internal/pb --go-grpc_opt=paths=source_relative `
  api/proto/greeter.proto
```

成功后应有：

```
internal/pb/
├── greeter.pb.go       # message 结构体
└── greeter_grpc.pb.go  # GreeterClient / GreeterServer / RegisterGreeterServer
```

### 可选：Makefile / 脚本

`gateway/scripts/gen-proto.ps1`：

```powershell
//切到项目根目录
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root
protoc `
  --proto_path=api/proto `
  --go_out=internal/pb --go_opt=paths=source_relative `
  --go-grpc_out=internal/pb --go-grpc_opt=paths=source_relative `
  api/proto/greeter.proto
Write-Host "generated internal/pb/"
```

### 阅读生成代码（必做）

打开 `greeter_grpc.pb.go`，关注：

```go
// Client 侧
type GreeterClient interface {
    SayHello(ctx context.Context, in *HelloRequest, opts ...grpc.CallOption) (*HelloReply, error)
}

// Server 侧需实现的接口
type GreeterServer interface {
    SayHello(context.Context, *HelloRequest) (*HelloReply, error)
    mustEmbedUnimplementedGreeterServer()
}

// 注册到 grpc.Server
func RegisterGreeterServer(s grpc.ServiceRegistrar, srv GreeterServer)
```

常量（代理转发时会看到）：

```go
const Greeter_SayHello_FullMethodName = "/demo.Greeter/SayHello"
```

### 验证编译

```powershell
go build ./...
```

若报错 `cannot find module`，确认 `go_package` 与 `go.mod` 的 `module go-proxy-demo` 一致。

### .gitignore 建议

生成代码**建议提交到仓库**（团队 CI 不依赖每人装 protoc），或在 CI 里跑 `protoc`。若忽略生成文件，需在 README 注明生成步骤。

### 检验标准

- [ ] `internal/pb/greeter.pb.go` 与 `greeter_grpc.pb.go` 存在
- [ ] `go build ./...` 通过
- [ ] 能指出 `GreeterServer` 与 `Greeter_SayHello_FullMethodName` 的位置

---

## 常见错误

| 现象 | 原因 | 处理 |
|------|------|------|
| `protoc-gen-go: program not found` | 插件不在 PATH | 安装插件并加入 `go/bin` |
| `Import "xxx" was not found` | `--proto_path` 不对 | 使用 `--proto_path=api/proto` |
| 生成文件包名不对 | `go_package` 写错 | 改为 `go-proxy-demo/internal/pb;pb` |
| `module declares path X but Y` | go_package 与 go.mod 不一致 | 对齐 module 名 |

---

## 模块结束标准

- [ ] 工具链安装完成
- [ ] `greeter.proto` 定义 SayHello unary RPC
- [ ] `internal/pb` 生成且可编译
- [ ] 理解 full method name 格式

**下一步** → [02-grpc-downstream.md](./02-grpc-downstream.md)：实现 gRPC 下游 Server。
