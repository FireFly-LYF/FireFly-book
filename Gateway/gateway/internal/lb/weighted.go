package lb

import "sync"

// WeightedRoundRobin 平滑加权轮询（Nginx smooth WRR 算法）
type WeightedRoundRobin struct {
	mu      sync.Mutex
	entries []weightedEntry
}

type weightedEntry struct {
	url           string
	weight        int
	currentWeight int
}

func NewWeightedRoundRobin() *WeightedRoundRobin {
	return &WeightedRoundRobin{}
}

func (w *WeightedRoundRobin) SetNodes(nodes []string) {
	weighted := make([]WeightedNode, len(nodes))
	for i, n := range nodes {
		weighted[i] = WeightedNode{URL: n, Weight: 1}
	}
	w.SetWeightedNodes(weighted)
}

func (w *WeightedRoundRobin) SetWeightedNodes(nodes []WeightedNode) {
	w.mu.Lock()
	defer w.mu.Unlock()

	entries := make([]weightedEntry, 0, len(nodes))
	for _, n := range nodes {
		if n.URL == "" || n.Weight <= 0 {
			continue
		}
		entries = append(entries, weightedEntry{
			url:    n.URL,
			weight: n.Weight,
		})
	}
	w.entries = entries
}

// 每次 NextKey 做三件事（key 忽略）：
//1. 每个节点：currentWeight += weight
//2. 选出 currentWeight 最大的节点
//3. 赢家：currentWeight -= 所有 weight 之和
func (w *WeightedRoundRobin) NextKey(_ string) (string, bool) {
	w.mu.Lock()
	defer w.mu.Unlock()

	if len(w.entries) == 0 {
		return "", false
	}

	total := 0
	var best *weightedEntry
	for i := range w.entries {
		e := &w.entries[i]
		e.currentWeight += e.weight
		total += e.weight
		if best == nil || e.currentWeight > best.currentWeight {
			best = e
		}
	}
	best.currentWeight -= total
	return best.url, true
}
