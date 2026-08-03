package registry

import (
	"errors"
	"sync"
	"time"

	"gateway/internal/lb"
	"gateway/internal/upstream"
)

var ErrDuplicate = errors.New("node already registered")
var ErrNotFound = errors.New("node not found")

// Memory 是 Registry 的内存实现：统一存储 HTTP / gRPC 下游，进程重启后数据丢失。
type Memory struct {
	mu    sync.RWMutex
	nodes map[string]*Node // key = Endpoint.ID
}

// NewMemory 用 yaml 解析后的端点列表初始化注册表。
func NewMemory(eps []upstream.Endpoint) *Memory {
	m := &Memory{nodes: make(map[string]*Node)}
	for _, ep := range eps {
		_ = m.Register(ep)
	}
	return m
}

// Register 动态添加下游；ID 重复时返回 ErrDuplicate。
func (m *Memory) Register(ep upstream.Endpoint) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.nodes[ep.ID]; ok {
		return ErrDuplicate
	}
	weight := ep.Weight
	if weight <= 0 {
		weight = 1
	}
	m.nodes[ep.ID] = &Node{
		ID:        ep.ID,
		HTTPURL:   ep.HTTPURL,
		GRPCAddr:  ep.GRPCAddr,
		TCPAddr:   ep.TCPAddr,
		Weight:    weight,
		Healthy:   true,
		LastCheck: time.Now(),
	}
	return nil
}

// Deregister 按 ID 下线节点。
func (m *Memory) Deregister(id string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.nodes[id]; !ok {
		return ErrNotFound
	}
	delete(m.nodes, id)
	return nil
}

// List 返回所有节点快照（含 unhealthy）。
func (m *Memory) List() []Node {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]Node, 0, len(m.nodes))
	for _, n := range m.nodes {
		out = append(out, *n)
	}
	return out
}

// ListHealthyHTTPURLs 返回健康且可 HTTP 转发的 base URL 列表。
func (m *Memory) ListHealthyHTTPURLs() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var urls []string
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		if u, ok := nodeHTTP(*n); ok {
			urls = append(urls, u)
		}
	}
	return urls
}

// ListHealthyGRPCAddrs 返回健康且可 gRPC 转发的 host:port 列表。
func (m *Memory) ListHealthyGRPCAddrs() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var addrs []string
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		if a, ok := nodeGRPC(*n); ok {
			addrs = append(addrs, a)
		}
	}
	return addrs
}

// ListHealthyTCPAddrs 返回健康且可 TCP 转发的 host:port 列表。
func (m *Memory) ListHealthyTCPAddrs() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var addrs []string
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		if a, ok := nodeTCP(*n); ok {
			addrs = append(addrs, a)
		}
	}
	return addrs
}

func (m *Memory) listHealthyHTTPWeighted() []lb.WeightedNode {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var out []lb.WeightedNode
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		u, ok := nodeHTTP(*n)
		if !ok {
			continue
		}
		out = append(out, lb.WeightedNode{URL: u, Weight: n.Weight})
	}
	return out
}

func (m *Memory) listHealthyGRPCWeighted() []lb.WeightedNode {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var out []lb.WeightedNode
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		a, ok := nodeGRPC(*n)
		if !ok {
			continue
		}
		out = append(out, lb.WeightedNode{URL: a, Weight: n.Weight})
	}
	return out
}

func (m *Memory) listHealthyTCPWeighted() []lb.WeightedNode {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var out []lb.WeightedNode
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		a, ok := nodeTCP(*n)
		if !ok {
			continue
		}
		out = append(out, lb.WeightedNode{URL: a, Weight: n.Weight})
	}
	return out
}

// SetHealth 更新节点健康状态；ID 不存在时静默忽略。
func (m *Memory) SetHealth(id string, healthy bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if n, ok := m.nodes[id]; ok {
		n.Healthy = healthy
		n.LastCheck = time.Now()
	}
}

func nodeHTTP(n Node) (string, bool) {
	return upstream.Endpoint{
		ID: n.ID, HTTPURL: n.HTTPURL, GRPCAddr: n.GRPCAddr, TCPAddr: n.TCPAddr, Weight: n.Weight,
	}.ResolvedHTTP()
}

func nodeGRPC(n Node) (string, bool) {
	return upstream.Endpoint{
		ID: n.ID, HTTPURL: n.HTTPURL, GRPCAddr: n.GRPCAddr, TCPAddr: n.TCPAddr, Weight: n.Weight,
	}.ResolvedGRPC()
}

func nodeTCP(n Node) (string, bool) {
	return upstream.Endpoint{
		ID: n.ID, HTTPURL: n.HTTPURL, GRPCAddr: n.GRPCAddr, TCPAddr: n.TCPAddr, Weight: n.Weight,
	}.ResolvedTCP()
}
