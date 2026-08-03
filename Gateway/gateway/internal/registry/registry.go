package registry

import (
	"time"

	"gateway/internal/upstream"
)

type Node struct {
	ID        string    `json:"id"`
	HTTPURL   string    `json:"http,omitempty"`
	GRPCAddr  string    `json:"grpc,omitempty"`
	TCPAddr   string    `json:"tcp,omitempty"`
	Weight    int       `json:"weight"`
	Healthy   bool      `json:"healthy"`
	LastCheck time.Time `json:"last_check"`
}

type Registry interface {
	Register(ep upstream.Endpoint) error
	Deregister(id string) error
	List() []Node
	ListHealthyHTTPURLs() []string
	ListHealthyGRPCAddrs() []string
	ListHealthyTCPAddrs() []string
	SetHealth(id string, healthy bool)
}
