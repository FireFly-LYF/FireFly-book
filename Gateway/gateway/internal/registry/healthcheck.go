package registry

import (
	"context"
	"log"
	"net"
	"net/http"
	"net/url"
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
		httpOK = httpProbePreferActuator(client, ep.HTTPURL)
	}
	if ep.ProbeGRPC() {
		grpcOK = tcpProbe(ep.GRPCAddr, 2*time.Second)
	}
	if ep.ProbeTCP() {
		tcpOK = tcpProbe(ep.TCPAddr, 2*time.Second)
	}
	return httpOK && grpcOK && tcpOK
}

func httpProbePreferActuator(client *http.Client, baseURL string) bool {
	// Java 服务：真探依赖；演示下游仍只有 /health
	if httpProbe(client, baseURL+"/actuator/health") {
		return true
	}
	if httpProbe(client, baseURL+"/health") {
		return true
	}
	// actuator 卡住（例如依赖探测阻塞）时：端口能连上仍视为可达，避免全量 503
	u, err := url.Parse(baseURL)
	if err != nil || u.Host == "" {
		return false
	}
	return tcpProbe(u.Host, 2*time.Second)
}

func httpProbe(client *http.Client, urlStr string) bool {
	req, _ := http.NewRequestWithContext(context.Background(), http.MethodGet, urlStr, nil)
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
