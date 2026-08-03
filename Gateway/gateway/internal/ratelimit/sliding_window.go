package ratelimit

import (
	"sync"
	"time"
)

// LocalSlidingWindow 进程内滑动窗口计数，Redis 不可用时的降级限流。
type LocalSlidingWindow struct {
	mu    sync.Mutex
	state map[string]*swState
}

type swState struct {
	buckets map[int64]int64
}

func NewLocalSlidingWindow() *LocalSlidingWindow {
	return &LocalSlidingWindow{
		state: make(map[string]*swState),
	}
}

// Allow 在 window 时间范围内最多允许 limit 次；limit<=0 表示不限制。
func (l *LocalSlidingWindow) Allow(key string, limit int64, window time.Duration) bool {
	if limit <= 0 {
		return true
	}

	l.mu.Lock()
	defer l.mu.Unlock()

	windowMs := window.Milliseconds()
	if windowMs < 1 {
		windowMs = 1
	}

	bucketWidthMs := windowMs / 10
	if bucketWidthMs < 1 {
		bucketWidthMs = 1
	}
	if windowMs > 3600*1000 {
		bucketWidthMs = 3600 * 1000
	}

	nowMs := time.Now().UnixMilli()
	currentBucket := nowMs / bucketWidthMs
	oldestBucket := (nowMs - windowMs) / bucketWidthMs

	st, ok := l.state[key]
	if !ok {
		st = &swState{buckets: make(map[int64]int64)}
		l.state[key] = st
	}

	for b := range st.buckets {
		if b < oldestBucket {
			delete(st.buckets, b)
		}
	}

	var total int64
	for b, count := range st.buckets {
		if b >= oldestBucket && b <= currentBucket {
			total += count
		}
	}
	if total >= limit {
		return false
	}

	st.buckets[currentBucket]++
	return true
}
