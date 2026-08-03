package lb

import (
	"sync"
	"sync/atomic"
)

type RoundRobin struct {
	mu    sync.RWMutex //保护 nodes 的读写
	nodes []string
	idx   atomic.Uint64
}

func NewRoundRobin() *RoundRobin {
	return &RoundRobin{}
}

func (r *RoundRobin) SetNodes(nodes []string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.nodes = append([]string(nil), nodes...)
}

func (r *RoundRobin) NextKey(_ string) (string, bool) {
	r.mu.RLock()
	n := len(r.nodes)
	if n == 0 {
		r.mu.RUnlock()
		return "", false
	}
	nodes := r.nodes
	r.mu.RUnlock()

	i := r.idx.Add(1) - 1
	return nodes[i%uint64(n)], true
}
