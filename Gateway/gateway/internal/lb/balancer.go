package lb

// Balancer 从节点列表中选出一个转发目标。
// key 供一致性 Hash 等策略使用；轮询/随机/加权等策略可忽略。
type Balancer interface {
	SetNodes(nodes []string)
	NextKey(key string) (target string, ok bool)
}

// WeightedBalancer 支持按权重配置节点
type WeightedBalancer interface {
	Balancer
	SetWeightedNodes(nodes []WeightedNode)
}