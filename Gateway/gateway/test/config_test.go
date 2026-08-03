package test

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"gateway/internal/config"
	"gateway/internal/lb"
)

func TestConfigLoad(t *testing.T) {
	path := filepath.Join("..", "internal", "config", "gateway.yaml")
	cfg, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}

	if cfg.Server.Addr != ":8080" {
		t.Fatalf("addr = %q", cfg.Server.Addr)
	}
	if string(cfg.JWTSecret()) != "phase3-dev-secret-change-me" {
		t.Fatalf("unexpected jwt secret")
	}

	ttl, err := cfg.TokenTTL()
	if err != nil || ttl != 24*time.Hour {
		t.Fatalf("token ttl = %v, err = %v", ttl, err)
	}

	lbCfg, err := cfg.BalancerConfig()
	if err != nil {
		t.Fatal(err)
	}
	if lbCfg.Strategy != lb.StrategyConsistentHash {
		t.Fatalf("strategy = %q", lbCfg.Strategy)
	}
	if len(lbCfg.Nodes) != 3 {
		t.Fatalf("nodes = %v", lbCfg.Nodes)
	}
	if cfg.Balancer() == nil {
		t.Fatal("balancer should be initialized after Load")
	}
}

func TestConfigLoad_InvalidFile(t *testing.T) {
	_, err := config.Load("not-exist.yaml")
	if err == nil {
		t.Fatal("expected error for missing config")
	}
}

func TestConfigLoad_InvalidYAML(t *testing.T) {
	f, err := os.CreateTemp(t.TempDir(), "bad-*.yaml")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.WriteString("server:\n  addr: ["); err != nil {
		t.Fatal(err)
	}
	f.Close()

	_, err = config.Load(f.Name())
	if err == nil {
		t.Fatal("expected parse error")
	}
}
