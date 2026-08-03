package lb

import (
	"fmt"
	"strings"
)

// Strategy 负载均衡策略名称
type Strategy string

const (
	StrategyRoundRobin     Strategy = "roundrobin"
	StrategyRandom         Strategy = "random"
	StrategyWeighted       Strategy = "weighted"
	StrategyConsistentHash Strategy = "consistent_hash"
)

// Config 创建 Balancer 的配置
type Config struct {
	Strategy Strategy
	Nodes    []string
	// Weights 仅 StrategyWeighted 使用；为空时每个节点权重为 1
	Weights []int
}

// ParseStrategy 解析策略名（不区分大小写，支持下划线/连字符）
func ParseStrategy(name string) (Strategy, error) {
	switch strings.ToLower(strings.ReplaceAll(name, "-", "_")) {
	case "roundrobin", "round_robin", "rr":
		return StrategyRoundRobin, nil
	case "random":
		return StrategyRandom, nil
	case "weighted", "weighted_round_robin", "wrr":
		return StrategyWeighted, nil
	case "consistent_hash", "consistenthash", "hash":
		return StrategyConsistentHash, nil
	default:
		return "", fmt.Errorf("unknown lb strategy %q, want roundrobin|random|weighted|consistent_hash", name)
	}
}

// NewBalancer 根据配置创建并初始化负载均衡器。
// nodes 可为空，由 registry.Sync 在启动后填入健康节点。
func NewBalancer(cfg Config) (Balancer, error) {
	var b Balancer
	switch cfg.Strategy {
	case StrategyRoundRobin:
		b = NewRoundRobin()
	case StrategyRandom:
		b = NewRandom()
	case StrategyWeighted:
		wb := NewWeightedRoundRobin()
		if len(cfg.Nodes) > 0 {
			wb.SetWeightedNodes(buildWeightedNodes(cfg.Nodes, cfg.Weights))
		}
		return wb, nil
	case StrategyConsistentHash:
		b = NewConsistentHash()
	default:
		return nil, fmt.Errorf("unsupported lb strategy %q", cfg.Strategy)
	}

	if len(cfg.Nodes) > 0 {
		b.SetNodes(cfg.Nodes)
	}
	return b, nil
}

func buildWeightedNodes(nodes []string, weights []int) []WeightedNode {
	out := make([]WeightedNode, len(nodes))
	for i, url := range nodes {
		weight := 1
		if i < len(weights) && weights[i] > 0 {
			weight = weights[i]
		}
		out[i] = WeightedNode{URL: url, Weight: weight}
	}
	return out
}
