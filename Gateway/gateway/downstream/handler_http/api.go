package handler_http

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
)

// Instance 由 cmd/downstream_http 启动时根据 --port 设置。
var Instance = "9001"

// WorkMS 模拟下游业务耗时（DB/RPC 等待），单位毫秒；0 表示不 sleep。
// /health 不走该逻辑，避免拖慢网关健康检查。
var WorkMS = 5

func User(c *gin.Context) {
	payload := businessWork("user")
	c.JSON(200, gin.H{
		"code": 0, "msg": "ok",
		"data": gin.H{
			"name":     "张三",
			"role":     "user",
			"instance": Instance,
			"profile": gin.H{
				"email": "zhangsan@example.com",
				"phone": "13800000000",
				"tags":  []string{"vip", "gateway-bench", "tenant-demo"},
				"score": 95.5,
				"meta":  payload,
			},
		},
	})
}

func Order(c *gin.Context) {
	payload := businessWork("order")
	c.JSON(200, gin.H{
		"code": 0, "msg": "ok",
		"data": gin.H{
			"orderId":  "ORD-001",
			"amount":   99.9,
			"currency": "CNY",
			"items": []gin.H{
				{"sku": "SKU-1001", "name": "商品A", "qty": 2, "price": 29.9},
				{"sku": "SKU-1002", "name": "商品B", "qty": 1, "price": 40.1},
			},
			"instance": Instance,
			"meta":     payload,
		},
	})
}

// Health 保持轻量，供注册中心 / 运维探活，不做业务模拟。
func Health(c *gin.Context) {
	c.JSON(200, gin.H{
		"code": 0, "msg": "ok",
		"data": gin.H{"status": "healthy", "instance": Instance},
	})
}

// businessWork 模拟一次下游处理：固定等待 + 少量 CPU（哈希）+ 组装字段。
func businessWork(kind string) gin.H {
	if WorkMS > 0 {
		time.Sleep(time.Duration(WorkMS) * time.Millisecond)
	}
	raw := fmt.Sprintf("%s|%s|%d", kind, Instance, time.Now().UnixNano())
	sum := sha256.Sum256([]byte(raw))
	return gin.H{
		"work_ms": WorkMS,
		"digest":  hex.EncodeToString(sum[:8]),
		"ts":      time.Now().UnixMilli(),
	}
}
