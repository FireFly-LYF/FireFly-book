package lb

import (
	"fmt"
	"hash/fnv"
	"sort"
	"sync"
)

const defaultVirtualReplicas = 150

// ConsistentHash 一致性 Hash：相同 key 总是路由到同一节点
type ConsistentHash struct {
	mu         sync.RWMutex      // 读写锁，因为 HTTP 请求是并发的，多个 goroutine 可能同时读/写 nodes
	nodes      []string          // 真实节点列表
	ring       []uint32          // 环上的虚拟节点
	hashToNode map[uint32]string // 虚拟节点到真实节点的映射
	replicas   int               //每台服务器在环上放几个虚拟点
}

func NewConsistentHash() *ConsistentHash {
	return &ConsistentHash{
		replicas: defaultVirtualReplicas,
	}
}

func (c *ConsistentHash) SetReplicas(n int) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if n <= 0 {
		n = defaultVirtualReplicas
	}
	c.replicas = n
	if len(c.nodes) > 0 {
		c.rebuildLocked(c.nodes)
	}
}

func (c *ConsistentHash) SetNodes(nodes []string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.nodes = append([]string(nil), nodes...)
	c.rebuildLocked(c.nodes)
}

func (c *ConsistentHash) rebuildLocked(nodes []string) {
	c.ring = c.ring[:0]
	c.hashToNode = make(map[uint32]string, len(nodes)*c.replicas)

	for _, node := range nodes {
		for i := range c.replicas {
			h := c.hashKey(node, i)
			c.ring = append(c.ring, h)
			c.hashToNode[h] = node
		}
	}
	sort.Slice(c.ring, func(i, j int) bool { return c.ring[i] < c.ring[j] })
}

func (c *ConsistentHash) hashKey(node string, replica int) uint32 {
	h := fnv.New32a()
	_, _ = fmt.Fprintf(h, "%s#%d", node, replica)
	return h.Sum32()
}

func (c *ConsistentHash) hashRequest(key string) uint32 {
	h := fnv.New32a()
	_, _ = h.Write([]byte(key))
	return h.Sum32()
}

// NextKey 根据 key（如客户端 IP）选节点；同一 key 结果稳定
func (c *ConsistentHash) NextKey(key string) (string, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()

	if len(c.ring) == 0 {
		return "", false
	}
	if key == "" {
		key = "default"
	}

	h := c.hashRequest(key)
	idx := sort.Search(len(c.ring), func(i int) bool { return c.ring[i] >= h })
	if idx == len(c.ring) {
		idx = 0
	}
	return c.hashToNode[c.ring[idx]], true
}
