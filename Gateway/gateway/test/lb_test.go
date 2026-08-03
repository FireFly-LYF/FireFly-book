package test

import (
	"fmt"
	"strings"
	"testing"

	"gateway/internal/lb"
)

var testNodes = []string{
	"http://localhost:9001",
	"http://localhost:9002",
	"http://localhost:9003",
}

func instanceOf(url string) string {
	return strings.TrimPrefix(url, "http://localhost:")
}

// TestRoundRobin_Order 轮询：连续 9 次应严格 9001→9002→9003 循环
func TestRoundRobin_Order(t *testing.T) {
	b := lb.NewRoundRobin()
	b.SetNodes(testNodes)

	want := []string{"9001", "9002", "9003", "9001", "9002", "9003", "9001", "9002", "9003"}
	got := make([]string, 0, len(want))

	for range want {
		target, ok := b.NextKey("")
		if !ok {
			t.Fatal("Next() returned false")
		}
		got = append(got, instanceOf(target))
	}

	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("第 %d 次: got %s, want %s\n全部结果: %v", i+1, got[i], want[i], got)
		}
	}
	t.Logf("RoundRobin 9 次结果: %v", got)
}

// TestRandom_Distributes 随机：大量请求后三个节点都应被命中
func TestRandom_Distributes(t *testing.T) {
	b := lb.NewRandom()
	b.SetNodes(testNodes)

	hits := map[string]int{}
	const rounds = 300
	for range rounds {
		target, ok := b.NextKey("")
		if !ok {
			t.Fatal("Next() returned false")
		}
		hits[target]++
	}

	for _, node := range testNodes {
		if hits[node] == 0 {
			t.Fatalf("Random 在 %d 次请求中从未命中 %s\n分布: %v", rounds, node, formatHits(hits))
		}
	}
	t.Logf("Random %d 次分布: %v", rounds, formatHits(hits))
}

// TestCompareAlgorithms 同一次运行里对比两种算法的前 9 次（直观看出差异）
func TestCompareAlgorithms(t *testing.T) {
	rr := lb.NewRoundRobin()
	rr.SetNodes(testNodes)

	rnd := lb.NewRandom()
	rnd.SetNodes(testNodes)

	rrResults := make([]string, 9)
	rndResults := make([]string, 9)

	for i := range 9 {
		target, ok := rr.NextKey("")
		if !ok {
			t.Fatal("RoundRobin Next() failed")
		}
		rrResults[i] = instanceOf(target)

		target, ok = rnd.NextKey("")
		if !ok {
			t.Fatal("Random Next() failed")
		}
		rndResults[i] = instanceOf(target)
	}

	t.Logf("RoundRobin 前 9 次: %v", rrResults)
	t.Logf("Random     前 9 次: %v", rndResults)

	// 轮询必须严格等于预期顺序
	wantRR := []string{"9001", "9002", "9003", "9001", "9002", "9003", "9001", "9002", "9003"}
	for i, w := range wantRR {
		if rrResults[i] != w {
			t.Errorf("RoundRobin[%d] = %s, want %s", i, rrResults[i], w)
		}
	}

	// 随机不断言固定顺序，只断言每个值都在合法集合内
	valid := map[string]bool{"9001": true, "9002": true, "9003": true}
	for i, inst := range rndResults {
		if !valid[inst] {
			t.Errorf("Random[%d] = %s, 不在 9001/9002/9003 中", i, inst)
		}
	}
}

func TestEmptyNodes(t *testing.T) {
	algorithms := []struct {
		name string
		b    lb.Balancer
	}{
		{"Random", lb.NewRandom()},
		{"RoundRobin", lb.NewRoundRobin()},
		{"WeightedRoundRobin", lb.NewWeightedRoundRobin()},
		{"ConsistentHash", lb.NewConsistentHash()},
	}

	for _, tc := range algorithms {
		t.Run(tc.name, func(t *testing.T) {
			target, ok := tc.b.NextKey("")
			if ok {
				t.Fatalf("%s: 空列表应返回 ok=false, got target=%q", tc.name, target)
			}
		})
	}
}

func formatHits(hits map[string]int) map[string]int {
	out := make(map[string]int, len(hits))
	for url, n := range hits {
		out[instanceOf(url)] = n
	}
	return out
}

// TestWeightedRoundRobin_Ratio 权重 1:2:3，600 次应接近 100:200:300
func TestWeightedRoundRobin_Ratio(t *testing.T) {
	w := lb.NewWeightedRoundRobin()
	w.SetWeightedNodes([]lb.WeightedNode{
		{URL: testNodes[0], Weight: 1},
		{URL: testNodes[1], Weight: 2},
		{URL: testNodes[2], Weight: 3},
	})

	hits := map[string]int{}
	const rounds = 600
	for range rounds {
		target, ok := w.NextKey("")
		if !ok {
			t.Fatal("Next() returned false")
		}
		hits[target]++
	}

	got := formatHits(hits)
	want := map[string]int{"9001": 100, "9002": 200, "9003": 300}
	for inst, wcount := range want {
		if got[inst] != wcount {
			t.Fatalf("权重 1:2:3 下 %s 命中 %d 次, want %d\n分布: %v", inst, got[inst], wcount, got)
		}
	}
	t.Logf("WeightedRoundRobin 600 次分布: %v", got)
}

// TestConsistentHash_SameKey 同一 key 始终落到同一节点
func TestConsistentHash_SameKey(t *testing.T) {
	ch := lb.NewConsistentHash()
	ch.SetNodes(testNodes)

	const key = "user-42"
	first, ok := ch.NextKey(key)
	if !ok {
		t.Fatal("NextKey failed")
	}

	for range 100 {
		target, ok := ch.NextKey(key)
		if !ok {
			t.Fatal("NextKey failed")
		}
		if target != first {
			t.Fatalf("同一 key 应稳定路由: first=%s, got=%s", instanceOf(first), instanceOf(target))
		}
	}
	t.Logf("key=%q 稳定路由到 instance=%s", key, instanceOf(first))
}

// TestConsistentHash_DifferentKeys 不同 key 可能落到不同节点（至少用到 2 个）
func TestConsistentHash_DifferentKeys(t *testing.T) {
	ch := lb.NewConsistentHash()
	ch.SetNodes(testNodes)

	hits := map[string]bool{}
	for i := range 50 {
		target, ok := ch.NextKey(fmt.Sprintf("client-%d", i))
		if !ok {
			t.Fatal("NextKey failed")
		}
		hits[target] = true
	}
	if len(hits) < 2 {
		t.Fatalf("50 个不同 key 只命中 %d 个节点, 期望至少 2 个", len(hits))
	}
	t.Logf("不同 key 命中 %d 个节点", len(hits))
}
