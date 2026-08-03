package test

import (
	"testing"
	"time"

	"gateway/internal/ratelimit"
)

func TestLocalSlidingWindow_AllowWithinLimit(t *testing.T) {
	limiter := ratelimit.NewLocalSlidingWindow()
	const limit int64 = 3

	for i := int64(0); i < limit; i++ {
		if !limiter.Allow("tenant-a", limit, time.Second) {
			t.Fatalf("request %d should pass", i+1)
		}
	}
}

func TestLocalSlidingWindow_ExceedsLimit(t *testing.T) {
	limiter := ratelimit.NewLocalSlidingWindow()
	const limit int64 = 3

	for i := int64(0); i < limit; i++ {
		limiter.Allow("tenant-a", limit, time.Second)
	}
	if limiter.Allow("tenant-a", limit, time.Second) {
		t.Fatal("request over limit should be denied")
	}
}

func TestLocalSlidingWindow_IndependentKeys(t *testing.T) {
	limiter := ratelimit.NewLocalSlidingWindow()
	const limit int64 = 2

	for i := int64(0); i < limit; i++ {
		if !limiter.Allow("tenant-a", limit, time.Second) {
			t.Fatal("tenant-a should pass")
		}
		if !limiter.Allow("tenant-b", limit, time.Second) {
			t.Fatal("tenant-b should pass")
		}
	}
}

func TestLocalSlidingWindow_WindowSlides(t *testing.T) {
	limiter := ratelimit.NewLocalSlidingWindow()
	const limit int64 = 2

	if !limiter.Allow("tenant-a", limit, 200*time.Millisecond) {
		t.Fatal("first request should pass")
	}
	if !limiter.Allow("tenant-a", limit, 200*time.Millisecond) {
		t.Fatal("second request should pass")
	}
	if limiter.Allow("tenant-a", limit, 200*time.Millisecond) {
		t.Fatal("third request should be denied")
	}

	time.Sleep(250 * time.Millisecond)
	if !limiter.Allow("tenant-a", limit, 200*time.Millisecond) {
		t.Fatal("request after window should pass")
	}
}

func TestLocalSlidingWindow_ZeroLimitMeansUnlimited(t *testing.T) {
	limiter := ratelimit.NewLocalSlidingWindow()
	for i := 0; i < 100; i++ {
		if !limiter.Allow("tenant-a", 0, time.Second) {
			t.Fatal("zero limit should allow all")
		}
	}
}
