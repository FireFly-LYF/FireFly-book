*错误处理约定*
| gRPC 错误 | 含义 | HTTP 近似 |
|-----------|------|-----------|
| `codes.OK` | 成功 | 200 |
| `codes.InvalidArgument` | 参数错误 | 400 |
| `codes.Unauthenticated` | 未鉴权 | 401 |
| `codes.Unavailable` | 服务不可用 | 503 |

*grpc传递路径*
grpcurl 连 localhost:50051，把 JSON 编成 protobuf，发 RPC：/gateway.UserService/Greet
网关 没有注册 UserService，所以由 UnknownServiceHandler 接住
director 从连接池取到 localhost:50052 的 ClientConn
grpc-proxy 按 fullMethodName 原样转发帧到下游
下游 :50052 才真正用 pb 解码请求、执行业务、编码响应

*jwt被绕过*
无 Token 经网关      ❌ 仍成功
伪造 Token           ❌ 仍成功
透明代理用了 UnknownServiceHandler，gRPC-Go 的 Unary 拦截器不会作用在未注册服务上，AuthUnaryInterceptor 实际上没执行
grpc-proxy 的 TransparentHandler 注册在 UnknownServiceHandler 上，走的是 Stream RPC 路径，不是普通已注册服务的 Unary 路径。