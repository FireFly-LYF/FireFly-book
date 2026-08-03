package registry

import (
	"fmt"

	"gateway/internal/lb"
)

// Balancers 同一套 LB 策略下的 HTTP / gRPC / TCP 三个视图。
// 节点列表来自同一 Registry，按协议过滤后分别 SetNodes。
type Balancers struct {
	HTTP lb.Balancer
	GRPC lb.Balancer
	TCP  lb.Balancer
}

// NewBalancers 根据 yaml 策略创建三个负载均衡器（状态独立，策略相同）。
func NewBalancers(cfg lb.Config) (*Balancers, error) {
	httpB, err := lb.NewBalancer(cfg)
	if err != nil {
		return nil, fmt.Errorf("http balancer: %w", err)
	}
	grpcB, err := lb.NewBalancer(cfg)
	if err != nil {
		return nil, fmt.Errorf("grpc balancer: %w", err)
	}
	tcpB, err := lb.NewBalancer(cfg)
	if err != nil {
		return nil, fmt.Errorf("tcp balancer: %w", err)
	}
	return &Balancers{HTTP: httpB, GRPC: grpcB, TCP: tcpB}, nil
}

// Sync 把注册中心里健康节点同步到 HTTP / gRPC / TCP 三个 balancer。
func Sync(reg Registry, bs *Balancers) {
	switch r := reg.(type) {
	case *Memory:
		syncWeighted(bs, r.listHealthyHTTPWeighted(), r.listHealthyGRPCWeighted(), r.listHealthyTCPWeighted(), reg)
	case *MySQL:
		syncWeighted(bs, r.listHealthyHTTPWeighted(), r.listHealthyGRPCWeighted(), r.listHealthyTCPWeighted(), reg)
	default:
		bs.HTTP.SetNodes(reg.ListHealthyHTTPURLs())
		bs.GRPC.SetNodes(reg.ListHealthyGRPCAddrs())
		bs.TCP.SetNodes(reg.ListHealthyTCPAddrs())
	}
}

func syncWeighted(bs *Balancers, httpW, grpcW, tcpW []lb.WeightedNode, reg Registry) {
	if wb, ok := bs.HTTP.(lb.WeightedBalancer); ok {
		wb.SetWeightedNodes(httpW)
	} else {
		bs.HTTP.SetNodes(reg.ListHealthyHTTPURLs())
	}
	if wb, ok := bs.GRPC.(lb.WeightedBalancer); ok {
		wb.SetWeightedNodes(grpcW)
	} else {
		bs.GRPC.SetNodes(reg.ListHealthyGRPCAddrs())
	}
	if wb, ok := bs.TCP.(lb.WeightedBalancer); ok {
		wb.SetWeightedNodes(tcpW)
	} else {
		bs.TCP.SetNodes(reg.ListHealthyTCPAddrs())
	}
}
