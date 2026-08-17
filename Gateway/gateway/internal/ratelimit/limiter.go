package ratelimit

import (
	"context"
	"log"
	"sort"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

// Verdict 限流判定结果。
type Verdict int

const (
	Allowed Verdict = iota
	DailyExceeded
	QPSExceeded
)

// RouteQuota 路径前缀对应的令牌桶参数。
type RouteQuota struct {
	Prefix   string
	Rate     int64
	Capacity int64
}

// Limiter 共享限流器；HTTP 按「身份键 + 路由配额」，gRPC 按租户默认配额。
type Limiter struct {
	rdb        *redis.Client
	rate       int64
	capacity   int64
	dailyLimit int64
	keyBy      string // user_ip | ip | tenant
	routes     []RouteQuota // 按 Prefix 长度降序
	fallback   *LocalSlidingWindow
	localOnly  bool
}

// NewLimiter 创建限流器；rdb=nil 时全程使用进程内滑动窗口降级。
// keyBy: user_ip（默认）| ip | tenant；routes 按最长前缀优先匹配。
func NewLimiter(rdb *redis.Client, rate, capacity, dailyLimit int64, keyBy string, routes []RouteQuota) *Limiter {
	cp := append([]RouteQuota(nil), routes...)
	sort.Slice(cp, func(i, j int) bool {
		return len(cp[i].Prefix) > len(cp[j].Prefix)
	})
	switch strings.ToLower(strings.TrimSpace(keyBy)) {
	case "ip", "tenant", "user_ip":
		keyBy = strings.ToLower(strings.TrimSpace(keyBy))
	default:
		keyBy = "user_ip"
	}
	return &Limiter{
		rdb:        rdb,
		rate:       rate,
		capacity:   capacity,
		dailyLimit: dailyLimit,
		keyBy:      keyBy,
		routes:     cp,
		fallback:   NewLocalSlidingWindow(),
		localOnly:  rdb == nil,
	}
}

// Allow 使用默认配额检查（gRPC / 兼容旧调用）；key 为空时按 anonymous。
func (l *Limiter) Allow(ctx context.Context, key string) Verdict {
	return l.AllowQuota(ctx, key, key, l.rate, l.capacity)
}

// AllowQuota 先按 identityKey 检查日配额，再按 bucketKey 检查 QPS。
// HTTP：identity=user/ip，bucket=identity|routeScope；gRPC：两者同为 tenant。
func (l *Limiter) AllowQuota(ctx context.Context, identityKey, bucketKey string, rate, capacity int64) Verdict {
	if identityKey == "" {
		identityKey = "anonymous"
	}
	if bucketKey == "" {
		bucketKey = identityKey
	}
	if rate <= 0 {
		rate = l.rate
	}
	if capacity <= 0 {
		capacity = rate
	}
	if capacity < rate {
		capacity = rate
	}
	if l.dailyLimit > 0 && !l.allowDaily(ctx, identityKey) {
		return DailyExceeded
	}
	if !l.allowQPS(ctx, bucketKey, rate, capacity) {
		return QPSExceeded
	}
	return Allowed
}

// ResolveQuota 按请求路径选路由配额；无匹配则用全局默认。
// scope 为命中的前缀（或 "default"），用于隔离不同路由的令牌桶。
func (l *Limiter) ResolveQuota(path string) (rate, capacity int64, scope string) {
	for _, r := range l.routes {
		if r.Prefix != "" && strings.HasPrefix(path, r.Prefix) {
			return r.Rate, r.Capacity, r.Prefix
		}
	}
	return l.rate, l.capacity, "default"
}

// KeyBy 返回限流键策略。
func (l *Limiter) KeyBy() string { return l.keyBy }

func (l *Limiter) allowDaily(ctx context.Context, key string) bool {
	useLocal := l.localOnly
	ok := false
	if !useLocal {
		var err error
		ok, err = QPDAllow(ctx, l.rdb, key, l.dailyLimit)
		if err != nil {
			log.Printf("ratelimit qpd redis error: %v, fallback to local sliding window", err)
			useLocal = true
		}
	}
	if useLocal {
		ok = l.fallback.Allow("qpd:"+key, l.dailyLimit, 24*time.Hour)
	}
	return ok
}

func (l *Limiter) allowQPS(ctx context.Context, key string, rate, capacity int64) bool {
	useLocal := l.localOnly
	ok := false
	if !useLocal {
		var err error
		ok, err = TokenBucketAllow(ctx, l.rdb, key, rate, capacity)
		if err != nil {
			log.Printf("ratelimit qps redis error: %v, fallback to local sliding window", err)
			useLocal = true
		}
	}
	if useLocal {
		ok = l.fallback.Allow("qps:"+key, capacity, time.Second)
	}
	return ok
}

func (l *Limiter) Rate() int64       { return l.rate }
func (l *Limiter) Capacity() int64   { return l.capacity }
func (l *Limiter) DailyLimit() int64 { return l.dailyLimit }
