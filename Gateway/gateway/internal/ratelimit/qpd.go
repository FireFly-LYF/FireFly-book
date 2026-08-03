package ratelimit

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

func QPDAllow(ctx context.Context, rdb *redis.Client, tenant string, dailyLimit int64) (bool, error) {
	day := time.Now().Format("20060102")
	key := fmt.Sprintf("ratelimit:qpd:%s:%s", tenant, day)

	n, err := rdb.Incr(ctx, key).Result()
	if err != nil {
		return false, err
	}
	if n == 1 {
		_ = rdb.Expire(ctx, key, 48*time.Hour).Err()
	}
	return n <= dailyLimit, nil
}
