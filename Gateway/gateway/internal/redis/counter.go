package redisx

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
)

// StatsRecorder 按租户记录成功请求量；HTTP / gRPC 共用，绑定一次 Redis 客户端。
type StatsRecorder struct {
	rdb *redis.Client
}

func NewStatsRecorder(rdb *redis.Client) *StatsRecorder {
	return &StatsRecorder{rdb: rdb}
}

// Record 成功请求 +1；rdb=nil 或 tenant 为空时静默跳过。
func (s *StatsRecorder) Record(ctx context.Context, tenant string) {
	if s == nil || s.rdb == nil || tenant == "" {
		return
	}
	if err := Incr(ctx, s.rdb, tenant); err != nil {
		log.Printf("stats incr failed: %v", err)
	}
}

// dayKey 生成按租户、按天维度的统计 key。
// 格式：stats:{tenant}:{YYYYMMDD}，例如 stats:tenant-a:20260704
func dayKey(tenant string, day time.Time) string {
	return fmt.Sprintf("stats:%s:%s", tenant, day.Format("20060102"))
}

// Incr 将指定租户「今天」的成功请求计数 +1。
// 使用 Pipeline 把 INCR 与 EXPIRE 打包发送，减少往返；key 保留约 90 天。
func Incr(ctx context.Context, rdb *redis.Client, tenant string) error {
	key := dayKey(tenant, time.Now())
	pipe := rdb.Pipeline()
	pipe.Incr(ctx, key)
	pipe.Expire(ctx, key, 90*24*time.Hour)
	_, err := pipe.Exec(ctx)
	return err
}

// Get 读取指定租户在某天的累计请求数；key 不存在时返回 0（不是错误）。
func Get(ctx context.Context, rdb *redis.Client, tenant string, day time.Time) (int64, error) {
	key := dayKey(tenant, day)
	n, err := rdb.Get(ctx, key).Int64()
	if err == redis.Nil {
		return 0, nil
	}
	return n, err
}

// DayCount 某天的请求量。
type DayCount struct {
	Date  string `json:"date"`
	Count int64  `json:"count"`
}

// GetLastNDays 返回最近 n 天（含今天）的日统计，按日期从旧到新排列。
func GetLastNDays(ctx context.Context, rdb *redis.Client, tenant string, n int) ([]DayCount, error) {
	if n <= 0 {
		n = 7
	}
	now := time.Now()
	keys := make([]string, n)
	dates := make([]string, n)
	for i := 0; i < n; i++ {
		day := now.AddDate(0, 0, -(n-1-i))
		dates[i] = day.Format("20060102")
		keys[i] = dayKey(tenant, day)
	}

	vals, err := rdb.MGet(ctx, keys...).Result()
	if err != nil {
		return nil, err
	}

	out := make([]DayCount, n)
	for i, v := range vals {
		var count int64
		if v != nil {
			switch val := v.(type) {
			case string:
				count, _ = strconv.ParseInt(val, 10, 64)
			case int64:
				count = val
			}
		}
		out[i] = DayCount{Date: dates[i], Count: count}
	}
	return out, nil
}
