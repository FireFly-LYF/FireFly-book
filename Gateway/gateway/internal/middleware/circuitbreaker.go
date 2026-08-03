package middleware

import (
	"sync"
	"time"
)

// CircuitBreaker 按下游节点（target 地址）维护熔断状态；HTTP 代理与 gRPC director 共用。
type CircuitBreaker struct {
	mu        sync.Mutex
	nodes     map[string]*cbNode
	threshold int
	cooldown  time.Duration
}

type cbNode struct {
	failures  int
	openUntil time.Time
}

// NewCircuitBreaker 创建节点级熔断器；threshold<=0 时 Allow 始终为 true。
func NewCircuitBreaker(threshold int, cooldown time.Duration) *CircuitBreaker {
	return &CircuitBreaker{
		nodes:     make(map[string]*cbNode),
		threshold: threshold,
		cooldown:  cooldown,
	}
}

// Allow 转发前检查节点是否处于开路状态。
func (c *CircuitBreaker) Allow(node string) bool {
	if c == nil || c.threshold <= 0 || node == "" {
		return true
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	st := c.getOrCreate(node)
	return !time.Now().Before(st.openUntil)
}

// Record 转发后上报结果；failed=true 表示下游失败（HTTP 5xx 或 gRPC error）。
func (c *CircuitBreaker) Record(node string, failed bool) {
	if c == nil || c.threshold <= 0 || node == "" {
		return
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	st := c.getOrCreate(node)
	if failed {
		st.failures++
		if st.failures >= c.threshold {
			st.openUntil = time.Now().Add(c.cooldown)
			st.failures = 0
		}
		return
	}
	st.failures = 0
}

func (c *CircuitBreaker) getOrCreate(node string) *cbNode {
	st, ok := c.nodes[node]
	if !ok {
		st = &cbNode{}
		c.nodes[node] = st
	}
	return st
}
