package test

import (
	"testing"

	"gateway/internal/lb"
)

func TestParseStrategy(t *testing.T) {
	cases := []struct {
		in   string
		want lb.Strategy
	}{
		{"roundrobin", lb.StrategyRoundRobin},
		{"ROUND_ROBIN", lb.StrategyRoundRobin},
		{"rr", lb.StrategyRoundRobin},
		{"random", lb.StrategyRandom},
		{"weighted", lb.StrategyWeighted},
		{"wrr", lb.StrategyWeighted},
		{"consistent_hash", lb.StrategyConsistentHash},
		{"hash", lb.StrategyConsistentHash},
	}

	for _, tc := range cases {
		got, err := lb.ParseStrategy(tc.in)
		if err != nil {
			t.Fatalf("ParseStrategy(%q) err: %v", tc.in, err)
		}
		if got != tc.want {
			t.Fatalf("ParseStrategy(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
}

func TestNewBalancer_AllStrategies(t *testing.T) {
	strategies := []lb.Strategy{
		lb.StrategyRoundRobin,
		lb.StrategyRandom,
		lb.StrategyWeighted,
		lb.StrategyConsistentHash,
	}

	for _, s := range strategies {
		t.Run(string(s), func(t *testing.T) {
			b, err := lb.NewBalancer(lb.Config{
				Strategy: s,
				Nodes:    testNodes,
				Weights:  []int{1, 2, 3},
			})
			if err != nil {
				t.Fatal(err)
			}
			target, ok := b.NextKey("test")
			if !ok || target == "" {
				t.Fatalf("strategy %s: NextKey failed", s)
			}
		})
	}
}

func TestNewBalancer_EmptyNodes(t *testing.T) {
	_, err := lb.NewBalancer(lb.Config{
		Strategy: lb.StrategyRoundRobin,
		Nodes:    nil,
	})
	if err == nil {
		t.Fatal("expected error for empty nodes")
	}
}
