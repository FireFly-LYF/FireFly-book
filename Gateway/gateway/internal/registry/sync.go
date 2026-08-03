package registry

import "gateway/internal/lb"

// RefreshBalancer 兼容旧调用：仅同步 HTTP 视图。
// 新代码请使用 Sync(reg, balancers)。
func RefreshBalancer(reg Registry, b lb.Balancer) {
	b.SetNodes(reg.ListHealthyHTTPURLs())
}
