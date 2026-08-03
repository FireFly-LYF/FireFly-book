// Package grpcserver 提供 gRPC 网关侧到下游的 ClientConn 连接池。
// 网关 director 每次转发需要 *grpc.ClientConn；池按 target 复用长连接，避免每个 RPC 都新建连接。
package grpcserver

import (
	"sync"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// Pool 按下游地址（如 "localhost:50052"）缓存 *grpc.ClientConn。
// gRPC 基于 HTTP/2 多路复用，同一 target 只需维护一条连接即可承载多个并发 RPC。
type Pool struct {
	mu    sync.Mutex                  // 保护 conns，Get/Close 可能并发调用
	conns map[string]*grpc.ClientConn // key = target host:port
}

// NewPool 创建空连接池；下游地址在首次 Get 时懒加载创建 ClientConn。
func NewPool() *Pool {
	return &Pool{conns: make(map[string]*grpc.ClientConn)}
}

// Get 返回指向 target 的 ClientConn：命中缓存则直接复用，否则 NewClient 并写入池。
// target 格式为 "host:port"，与 lb.Balancer.NextKey 返回的地址一致。
func (p *Pool) Get(target string) (*grpc.ClientConn, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if c, ok := p.conns[target]; ok {
		return c, nil
	}
	// NewClient 仅创建虚拟连接，不立即建 TCP；首次 RPC 时自动连接并支持断线重连。
	c, err := grpc.NewClient(target, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return nil, err
	}
	p.conns[target] = c
	return c, nil
}

// Close 关闭池中全部连接并清空 map，网关进程退出时调用。
func (p *Pool) Close() {
	p.mu.Lock()
	defer p.mu.Unlock()
	for _, c := range p.conns {
		_ = c.Close()
	}
	p.conns = make(map[string]*grpc.ClientConn)
}
