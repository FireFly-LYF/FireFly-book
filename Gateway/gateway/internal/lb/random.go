package lb

import (
	"math/rand"
	"sync"
)

type Random struct {
	//读写锁，因为 HTTP 请求是并发的，多个 goroutine 可能同时读/写 nodes
	mu sync.RWMutex
	//节点列表
	nodes []string
}

// 创建一个新的随机负载均衡器
func NewRandom() *Random {
	return &Random{}
}

// 设置节点列表
func (r *Random) SetNodes(nodes []string) {
	r.mu.Lock()
	//锁定，防止多个 goroutine 同时写 nodes
	defer r.mu.Unlock()
	//复制一份新的节点列表，防止多个 goroutine 同时读/写 nodes
	r.nodes = append([]string(nil), nodes...)
}

// 从节点列表中选出一个转发目标
func (r *Random) NextKey(_ string) (string, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	n := len(r.nodes)
	if n == 0 {
		return "", false
	}
	return r.nodes[rand.Intn(n)], true
}
