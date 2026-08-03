// Package upstream 将 yaml/API 中的下游配置规范为统一端点。
// HTTP、gRPC、TCP 地址仅来自显式配置，不做跨协议推导。
package upstream

import (
	"errors"
	"fmt"
	"net/url"
	"strings"
)

// Endpoint 描述一个逻辑下游节点，可同时或分别支持 HTTP、gRPC、TCP。
type Endpoint struct {
	ID       string // 注册表主键，用于去重与展示
	HTTPURL  string // 显式配置的 HTTP 地址（含 http://）
	GRPCAddr string // 显式配置的 gRPC 地址（host:port）
	TCPAddr  string // 显式配置的 TCP 地址（host:port）
	Weight   int
}

// Normalize 把 http / grpc / tcp / addr 四种写法合并为一个 Endpoint。
//
// 规则：
//   - addr：同时声明 HTTP、gRPC、TCP（同一 host:port）
//   - 任意组合：http、grpc、tcp 可单独或组合填写
func Normalize(httpURL, grpcAddr, tcpAddr, addr string, weight int) (Endpoint, error) {
	if weight <= 0 {
		weight = 1
	}

	if addr != "" {
		hp, err := toHostPort(addr)
		if err != nil {
			return Endpoint{}, err
		}
		if httpURL == "" {
			httpURL = "http://" + hp
		}
		if grpcAddr == "" {
			grpcAddr = hp
		}
		if tcpAddr == "" {
			tcpAddr = hp
		}
	}

	if httpURL == "" && grpcAddr == "" && tcpAddr == "" {
		return Endpoint{}, errors.New("upstream requires http, grpc, tcp, or addr")
	}

	if grpcAddr != "" {
		hp, err := toHostPort(grpcAddr)
		if err != nil {
			return Endpoint{}, err
		}
		grpcAddr = hp
	}
	if tcpAddr != "" {
		hp, err := toHostPort(tcpAddr)
		if err != nil {
			return Endpoint{}, err
		}
		tcpAddr = hp
	}

	ep := Endpoint{
		HTTPURL:  httpURL,
		GRPCAddr: grpcAddr,
		TCPAddr:  tcpAddr,
		Weight:   weight,
	}
	ep.ID = idFromEndpoint(ep)
	return ep, nil
}

// ResolvedHTTP 返回显式配置的 HTTP 地址；未配置 http 时不可用。
func (e Endpoint) ResolvedHTTP() (string, bool) {
	if e.HTTPURL == "" {
		return "", false
	}
	return e.HTTPURL, true
}

// ResolvedGRPC 返回显式配置的 gRPC 地址；未配置 grpc 时不可用。
func (e Endpoint) ResolvedGRPC() (string, bool) {
	if e.GRPCAddr == "" {
		return "", false
	}
	return e.GRPCAddr, true
}

// ResolvedTCP 返回显式配置的 TCP 地址；未配置 tcp 时不可用。
func (e Endpoint) ResolvedTCP() (string, bool) {
	if e.TCPAddr == "" {
		return "", false
	}
	return e.TCPAddr, true
}

// ProbeHTTP 是否应对该节点做 HTTP /health 探测。
func (e Endpoint) ProbeHTTP() bool { return e.HTTPURL != "" }

// ProbeGRPC 是否应对该节点做 gRPC TCP 探测。
func (e Endpoint) ProbeGRPC() bool { return e.GRPCAddr != "" }

// ProbeTCP 是否应对该节点做 TCP 端口探测。
func (e Endpoint) ProbeTCP() bool { return e.TCPAddr != "" }

func toHostPort(v string) (string, error) {
	v = strings.TrimSpace(v)
	if v == "" {
		return "", errors.New("empty addr")
	}
	if strings.Contains(v, "://") {
		return hostPortFromURL(v)
	}
	return v, nil
}

func hostPortFromURL(raw string) (string, error) {
	u, err := url.Parse(raw)
	if err != nil {
		return "", err
	}
	if u.Host == "" {
		return "", fmt.Errorf("invalid url %q: missing host", raw)
	}
	return u.Host, nil
}

func idFromPair(httpURL, grpcAddr string) string {
	return httpURL + "|" + grpcAddr
}

func idFromEndpoint(ep Endpoint) string {
	switch {
	case ep.HTTPURL != "" && ep.GRPCAddr != "":
		return idFromPair(ep.HTTPURL, ep.GRPCAddr)
	case ep.HTTPURL != "":
		return ep.HTTPURL
	case ep.GRPCAddr != "":
		return "grpc:" + ep.GRPCAddr
	case ep.TCPAddr != "":
		return "tcp:" + ep.TCPAddr
	default:
		return ""
	}
}
