package ratelimit

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

const tokenBucketLua = `
local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1]) or tonumber(ARGV[3])
local ts = tonumber(data[2]) or tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local now = tonumber(ARGV[1])
local delta = math.max(0, now - ts) / 1000.0
tokens = math.min(capacity, tokens + delta * rate)
if tokens < 1 then
  return 0
end
tokens = tokens - 1
redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', KEYS[1], 60)
return 1
`

var tokenBucketScript = redis.NewScript(tokenBucketLua)

func TokenBucketAllow(ctx context.Context, rdb *redis.Client, key string, rate, capacity int64) (bool, error) {
	redisKey := fmt.Sprintf("ratelimit:bucket:%s", key)
	now := time.Now().UnixMilli()
	n, err := tokenBucketScript.Run(ctx, rdb, []string{redisKey}, now, rate, capacity).Int()
	if err != nil {
		return false, err
	}
	return n == 1, nil
}
