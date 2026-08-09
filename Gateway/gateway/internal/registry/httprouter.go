package registry

import (
	"fmt"
	"sort"
	"strings"
	"sync"

	"gateway/internal/upstream"
)

// HTTPRoute 一条按路径前缀转发的 HTTP 路由（独立注册表 + LB）。
type HTTPRoute struct {
	ID           string
	Prefix       string
	AuthRequired bool
	Reg          Registry
	Balancers    *Balancers
}

// RoutedNode 带路由信息的节点（管理面列表用）。
type RoutedNode struct {
	RouteID string `json:"route_id"`
	Prefix  string `json:"prefix"`
	Node
}

// HTTPRouter 最长前缀匹配；用于 /api/user → user 组 等。
type HTTPRouter struct {
	mu     sync.RWMutex
	routes []*HTTPRoute // 按 prefix 长度降序
	byID   map[string]*HTTPRoute
}

// NewHTTPRouter 创建空路由表。
func NewHTTPRouter() *HTTPRouter {
	return &HTTPRouter{byID: make(map[string]*HTTPRoute)}
}

// Add 注册一条路由；prefix / id 重复时返回错误。
func (r *HTTPRouter) Add(route *HTTPRoute) error {
	if route == nil || route.Prefix == "" || route.ID == "" {
		return fmt.Errorf("route id and prefix required")
	}
	if !strings.HasPrefix(route.Prefix, "/") {
		return fmt.Errorf("route prefix must start with /")
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if _, ok := r.byID[route.ID]; ok {
		return fmt.Errorf("duplicate route id %q", route.ID)
	}
	for _, existing := range r.routes {
		if existing.Prefix == route.Prefix {
			return fmt.Errorf("duplicate route prefix %q", route.Prefix)
		}
	}
	r.byID[route.ID] = route
	r.routes = append(r.routes, route)
	sort.Slice(r.routes, func(i, j int) bool {
		return len(r.routes[i].Prefix) > len(r.routes[j].Prefix)
	})
	return nil
}

// Match 按最长前缀匹配；要求精确边界：/api/user 匹配 /api/user 与 /api/user/xx，不匹配 /api/username。
func (r *HTTPRouter) Match(path string) (*HTTPRoute, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	for _, route := range r.routes {
		if path == route.Prefix || strings.HasPrefix(path, route.Prefix+"/") {
			return route, true
		}
	}
	return nil, false
}

// Get 按 id 或 prefix 查找。
func (r *HTTPRouter) Get(idOrPrefix string) (*HTTPRoute, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	if route, ok := r.byID[idOrPrefix]; ok {
		return route, true
	}
	for _, route := range r.routes {
		if route.Prefix == idOrPrefix {
			return route, true
		}
	}
	return nil, false
}

// Routes 返回路由快照。
func (r *HTTPRouter) Routes() []*HTTPRoute {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]*HTTPRoute, len(r.routes))
	copy(out, r.routes)
	return out
}

// ListAll 汇总所有路由下的节点。
func (r *HTTPRouter) ListAll() []RoutedNode {
	r.mu.RLock()
	defer r.mu.RUnlock()
	var out []RoutedNode
	for _, route := range r.routes {
		for _, n := range route.Reg.List() {
			out = append(out, RoutedNode{RouteID: route.ID, Prefix: route.Prefix, Node: n})
		}
	}
	return out
}

// RegisterTo 向指定路由注册下游。
func (r *HTTPRouter) RegisterTo(idOrPrefix string, ep upstream.Endpoint) error {
	route, ok := r.Get(idOrPrefix)
	if !ok {
		return fmt.Errorf("%w: route %q", ErrNotFound, idOrPrefix)
	}
	if err := route.Reg.Register(ep); err != nil {
		return err
	}
	Sync(route.Reg, route.Balancers)
	return nil
}

// DeregisterFrom 从指定路由下线；route 为空时在所有路由中按 id 查找。
func (r *HTTPRouter) DeregisterFrom(idOrPrefix, nodeID string) error {
	if idOrPrefix != "" {
		route, ok := r.Get(idOrPrefix)
		if !ok {
			return fmt.Errorf("%w: route %q", ErrNotFound, idOrPrefix)
		}
		if err := route.Reg.Deregister(nodeID); err != nil {
			return err
		}
		Sync(route.Reg, route.Balancers)
		return nil
	}
	r.mu.RLock()
	routes := append([]*HTTPRoute(nil), r.routes...)
	r.mu.RUnlock()
	for _, route := range routes {
		if err := route.Reg.Deregister(nodeID); err == nil {
			Sync(route.Reg, route.Balancers)
			return nil
		}
	}
	return ErrNotFound
}
