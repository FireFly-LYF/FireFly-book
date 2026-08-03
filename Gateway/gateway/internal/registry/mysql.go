package registry

import (
	"database/sql"
	"errors"
	"sync"
	"time"

	"gateway/internal/lb"
	"gateway/internal/upstream"

	mysqldriver "github.com/go-sql-driver/mysql"
)

// MySQL 实现 Registry：写 DB 持久化，内存 map 供 LB / 健康检查快读。
type MySQL struct {
	db    *sql.DB
	mu    sync.RWMutex
	nodes map[string]*Node
}

// NewMySQL 连接 MySQL，从 upstreams 表恢复节点；表为空时用 seed 写入 yaml 初始节点。
func NewMySQL(dsn string, seed []upstream.Endpoint) (*MySQL, error) {
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)

	if err := db.Ping(); err != nil {
		_ = db.Close()
		return nil, err
	}

	m := &MySQL{db: db, nodes: make(map[string]*Node)}
	if err := m.loadAll(); err != nil {
		_ = db.Close()
		return nil, err
	}
	if len(m.nodes) == 0 && len(seed) > 0 {
		for _, ep := range seed {
			if err := m.Register(ep); err != nil && !errors.Is(err, ErrDuplicate) {
				_ = db.Close()
				return nil, err
			}
		}
	}
	return m, nil
}

func (m *MySQL) loadAll() error {
	rows, err := m.db.Query(`
		SELECT id, http_url, grpc_addr, tcp_addr, weight, healthy, last_check
		FROM upstreams`)
	if err != nil {
		return err
	}
	defer rows.Close()

	nodes := make(map[string]*Node)
	for rows.Next() {
		var (
			id        string
			httpURL   sql.NullString
			grpcAddr  sql.NullString
			tcpAddr   sql.NullString
			weight    int
			healthy   int
			lastCheck sql.NullTime
		)
		if err := rows.Scan(&id, &httpURL, &grpcAddr, &tcpAddr, &weight, &healthy, &lastCheck); err != nil {
			return err
		}
		n := &Node{
			ID:       id,
			HTTPURL:  httpURL.String,
			GRPCAddr: grpcAddr.String,
			TCPAddr:  tcpAddr.String,
			Weight:   weight,
			Healthy:  healthy != 0,
		}
		if lastCheck.Valid {
			n.LastCheck = lastCheck.Time
		}
		if n.Weight <= 0 {
			n.Weight = 1
		}
		nodes[id] = n
	}
	if err := rows.Err(); err != nil {
		return err
	}

	m.mu.Lock()
	m.nodes = nodes
	m.mu.Unlock()
	return nil
}

func (m *MySQL) Register(ep upstream.Endpoint) error {
	weight := ep.Weight
	if weight <= 0 {
		weight = 1
	}
	now := time.Now()

	m.mu.Lock()
	if _, ok := m.nodes[ep.ID]; ok {
		m.mu.Unlock()
		return ErrDuplicate
	}
	m.mu.Unlock()

	_, err := m.db.Exec(`
		INSERT INTO upstreams (id, http_url, grpc_addr, tcp_addr, weight, healthy, last_check)
		VALUES (?, ?, ?, ?, ?, 1, ?)`,
		ep.ID, nullIfEmpty(ep.HTTPURL), nullIfEmpty(ep.GRPCAddr), nullIfEmpty(ep.TCPAddr), weight, now,
	)
	if err != nil {
		if isDuplicateKey(err) {
			return ErrDuplicate
		}
		return err
	}

	node := &Node{
		ID:        ep.ID,
		HTTPURL:   ep.HTTPURL,
		GRPCAddr:  ep.GRPCAddr,
		TCPAddr:   ep.TCPAddr,
		Weight:    weight,
		Healthy:   true,
		LastCheck: now,
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.nodes[ep.ID]; ok {
		return ErrDuplicate
	}
	m.nodes[ep.ID] = node
	return nil
}

func (m *MySQL) Deregister(id string) error {
	m.mu.RLock()
	_, ok := m.nodes[id]
	m.mu.RUnlock()
	if !ok {
		return ErrNotFound
	}

	if _, err := m.db.Exec(`DELETE FROM upstreams WHERE id = ?`, id); err != nil {
		return err
	}

	m.mu.Lock()
	delete(m.nodes, id)
	m.mu.Unlock()
	return nil
}

func (m *MySQL) List() []Node {
	m.mu.RLock()
	defer m.mu.RUnlock()
	out := make([]Node, 0, len(m.nodes))
	for _, n := range m.nodes {
		out = append(out, *n)
	}
	return out
}

func (m *MySQL) ListHealthyHTTPURLs() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var urls []string
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		if u, ok := nodeHTTP(*n); ok {
			urls = append(urls, u)
		}
	}
	return urls
}

func (m *MySQL) ListHealthyGRPCAddrs() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var addrs []string
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		if a, ok := nodeGRPC(*n); ok {
			addrs = append(addrs, a)
		}
	}
	return addrs
}

func (m *MySQL) ListHealthyTCPAddrs() []string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var addrs []string
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		if a, ok := nodeTCP(*n); ok {
			addrs = append(addrs, a)
		}
	}
	return addrs
}

func (m *MySQL) listHealthyHTTPWeighted() []lb.WeightedNode {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var out []lb.WeightedNode
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		u, ok := nodeHTTP(*n)
		if !ok {
			continue
		}
		out = append(out, lb.WeightedNode{URL: u, Weight: n.Weight})
	}
	return out
}

func (m *MySQL) listHealthyGRPCWeighted() []lb.WeightedNode {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var out []lb.WeightedNode
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		a, ok := nodeGRPC(*n)
		if !ok {
			continue
		}
		out = append(out, lb.WeightedNode{URL: a, Weight: n.Weight})
	}
	return out
}

func (m *MySQL) listHealthyTCPWeighted() []lb.WeightedNode {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var out []lb.WeightedNode
	for _, n := range m.nodes {
		if !n.Healthy {
			continue
		}
		a, ok := nodeTCP(*n)
		if !ok {
			continue
		}
		out = append(out, lb.WeightedNode{URL: a, Weight: n.Weight})
	}
	return out
}

// SetHealth 仅更新内存；健康检查频繁，不写 DB。
func (m *MySQL) SetHealth(id string, healthy bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if n, ok := m.nodes[id]; ok {
		n.Healthy = healthy
		n.LastCheck = time.Now()
	}
}

func nullIfEmpty(s string) any {
	if s == "" {
		return nil
	}
	return s
}

func isDuplicateKey(err error) bool {
	var me *mysqldriver.MySQLError
	return errors.As(err, &me) && me.Number == 1062
}
