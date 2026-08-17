package ratelimit

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

func QPDAllow(ctx context.Context, rdb *redis.Client, key string, dailyLimit int64) (bool, error) {
	day := time.Now().Format("20060102")
	redisKey := fmt.Sprintf("ratelimit:qpd:%s:%s", key, day)

	n, err := rdb.Incr(ctx, redisKey).Result()
	if err != nil {
		return false, err
	}
	if n == 1 {
		_ = rdb.Expire(ctx, redisKey, 48*time.Hour).Err()
	}
	return n <= dailyLimit, nil
}
