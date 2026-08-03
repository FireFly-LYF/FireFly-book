package ratelimit

import (
	"context"
	"log"
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

// Limiter 按租户统一限流；HTTP 中间件与 gRPC interceptor 共用同一实例，配额不区分协议。
type Limiter struct {
	rdb        *redis.Client
	rate       int64
	capacity   int64
	dailyLimit int64
	fallback   *LocalSlidingWindow
	localOnly  bool
}

// NewLimiter 创建租户级限流器；rdb=nil 时全程使用进程内滑动窗口降级。
func NewLimiter(rdb *redis.Client, rate, capacity, dailyLimit int64) *Limiter {
	return &Limiter{
		rdb:        rdb,
		rate:       rate,
		capacity:   capacity,
		dailyLimit: dailyLimit,
		fallback:   NewLocalSlidingWindow(),
		localOnly:  rdb == nil,
	}
}

// Allow 检查租户是否允许通过：先 QPD，再 QPS。tenant 为空时按 anonymous 计。
func (l *Limiter) Allow(ctx context.Context, tenant string) Verdict {
	if tenant == "" {
		tenant = "anonymous"
	}
	if l.dailyLimit > 0 && !l.allowDaily(ctx, tenant) {
		return DailyExceeded
	}
	qpsLimit := l.capacity
	if qpsLimit <= 0 {
		qpsLimit = l.rate
	}
	if !l.allowQPS(ctx, tenant, qpsLimit) {
		return QPSExceeded
	}
	return Allowed
}

func (l *Limiter) allowDaily(ctx context.Context, tenant string) bool {
	useLocal := l.localOnly
	ok := false
	if !useLocal {
		var err error
		ok, err = QPDAllow(ctx, l.rdb, tenant, l.dailyLimit)
		if err != nil {
			log.Printf("ratelimit qpd redis error: %v, fallback to local sliding window", err)
			useLocal = true
		}
	}
	if useLocal {
		ok = l.fallback.Allow("qpd:"+tenant, l.dailyLimit, 24*time.Hour)
	}
	return ok
}

func (l *Limiter) allowQPS(ctx context.Context, tenant string, qpsLimit int64) bool {
	useLocal := l.localOnly
	ok := false
	if !useLocal {
		var err error
		ok, err = TokenBucketAllow(ctx, l.rdb, tenant, l.rate, l.capacity)
		if err != nil {
			log.Printf("ratelimit qps redis error: %v, fallback to local sliding window", err)
			useLocal = true
		}
	}
	if useLocal {
		ok = l.fallback.Allow("qps:"+tenant, qpsLimit, time.Second)
	}
	return ok
}

func (l *Limiter) Rate() int64       { return l.rate }
func (l *Limiter) Capacity() int64   { return l.capacity }
func (l *Limiter) DailyLimit() int64 { return l.dailyLimit }
