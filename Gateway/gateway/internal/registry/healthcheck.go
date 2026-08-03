package registry

import (
	"context"
	"log"
	"net"
	"net/http"
	"time"

	"gateway/internal/upstream"
)

// StartHealthCheck 统一健康检查：按节点声明的协议分别探测 HTTP /health 或 TCP 端口。
// 每轮结束后 Sync 到 HTTP / gRPC / TCP 三个 balancer 视图。
func StartHealthCheck(reg Registry, bs *Balancers, interval time.Duration) {
	client := &http.Client{Timeout: 2 * time.Second}
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for range ticker.C {
			for _, node := range reg.List() {
				ep := upstream.Endpoint{
					ID: node.ID, HTTPURL: node.HTTPURL, GRPCAddr: node.GRPCAddr, TCPAddr: node.TCPAddr, Weight: node.Weight,
				}
				healthy := probeEndpoint(client, ep)
				reg.SetHealth(node.ID, healthy)
				if !healthy {
					log.Printf("health check failed: %s", node.ID)
				}
			}
			Sync(reg, bs)
		}
	}()
}

func probeEndpoint(client *http.Client, ep upstream.Endpoint) bool {
	httpOK := true
	grpcOK := true
	tcpOK := true

	if ep.ProbeHTTP() {
		httpOK = httpProbe(client, ep.HTTPURL+"/health")
	}
	if ep.ProbeGRPC() {
		grpcOK = tcpProbe(ep.GRPCAddr, 2*time.Second)
	}
	if ep.ProbeTCP() {
		tcpOK = tcpProbe(ep.TCPAddr, 2*time.Second)
	}
	return httpOK && grpcOK && tcpOK
}

func httpProbe(client *http.Client, url string) bool {
	req, _ := http.NewRequestWithContext(context.Background(), http.MethodGet, url, nil)
	resp, err := client.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	return resp.StatusCode == http.StatusOK
}

func tcpProbe(addr string, timeout time.Duration) bool {
	conn, err := net.DialTimeout("tcp", addr, timeout)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}
