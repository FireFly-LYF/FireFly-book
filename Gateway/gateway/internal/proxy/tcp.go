// Package proxy 提供 HTTP / gRPC / TCP 三类透明转发。
package proxy

import (
	"io"
	"log"
	"net"
	"sync"

	"gateway/internal/lb"
	"gateway/internal/registry"
)

// RunTCP 启动 TCP 透明代理网关。
//
// 与 HTTP/gRPC 不同，TCP 代理工作在连接级：不解析应用层协议，只做字节流双向转发。
// 每个客户端连接独立 goroutine 处理；路由 key 为客户端 IP（供 consistent_hash 等策略使用）。
func RunTCP(listen string, balancer lb.Balancer, cb *registry.CircuitBreaker, blocklist map[string]struct{}) error {
	ln, err := net.Listen("tcp", listen)
	if err != nil {
		return err
	}
	log.Printf("tcp gateway listening on %s", listen)

	for {
		client, err := ln.Accept()
		if err != nil {
			log.Printf("tcp accept: %v", err)
			continue
		}
		// 连接建立后立即交给独立 goroutine，避免阻塞 Accept 循环
		go handleTCP(client, balancer, cb, blocklist)
	}
}

// handleTCP 处理单条 TCP 连接：选下游 → Dial → 双向 io.Copy 直到任一端关闭。
func handleTCP(client net.Conn, b lb.Balancer, cb *registry.CircuitBreaker, blocklist map[string]struct{}) {
	defer client.Close()

	// ① 治理：IP 黑名单（连接级，无法像 HTTP 那样读 JWT）
	key := remoteHost(client)
	if _, blocked := blocklist[key]; blocked {
		return
	}

	// ② 负载均衡：以客户端 IP 为 hash key，与 grcp.go 的 clientIP 策略一致
	target, ok := b.NextKey(key)
	if !ok {
		return
	}
	if cb != nil && !cb.Allow(target) {
		return
	}

	// ③ 建立到下游的 TCP 连接；Dial 失败记一次熔断
	upstream, err := net.Dial("tcp", target)
	if err != nil {
		if cb != nil {
			cb.Record(target, true)
		}
		return
	}

	// ④ 双向转发：client ↔ upstream 各起一个 goroutine
	// closeBoth 保证两端只关闭一次（两个 copy 协程都可能触发）
	var once sync.Once
	closeBoth := func() {
		once.Do(func() {
			_ = upstream.Close()
			_ = client.Close()
		})
	}

	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		copyAndHalfClose(upstream, client) // 客户端 → 下游
		closeBoth()
	}()
	go func() {
		defer wg.Done()
		copyAndHalfClose(client, upstream) // 下游 → 客户端
		closeBoth()
	}()
	wg.Wait()

	// 连接正常结束视为成功（TCP 无 HTTP 状态码）
	if cb != nil {
		cb.Record(target, false)
	}
}

// copyAndHalfClose 将 src 的字节流写入 dst，写完后半关闭写端。
//
// CloseWrite 发送 FIN，让对端 io.Copy 读到 EOF 而非一直阻塞；
// 这是 TCP 双向代理的标准做法（类似 nginx stream 的 proxy_half_close）。
func copyAndHalfClose(dst, src net.Conn) {
	_, _ = io.Copy(dst, src)
	if tc, ok := dst.(*net.TCPConn); ok {
		_ = tc.CloseWrite()
	}
}

// remoteHost 从 net.Conn 提取客户端 IP，供 LB 与 IP 黑名单使用。
func remoteHost(conn net.Conn) string {
	host, _, err := net.SplitHostPort(conn.RemoteAddr().String())
	if err != nil {
		return conn.RemoteAddr().String()
	}
	return host
}
