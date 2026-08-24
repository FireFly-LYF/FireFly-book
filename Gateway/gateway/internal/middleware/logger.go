package middleware

import (
	"crypto/rand"
	"encoding/hex"
	"log"
	"time"

	"github.com/gin-gonic/gin"
)

const headerRequestID = "X-Request-Id"

func RequestLogger() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		path := c.Request.URL.Path

		rid := c.GetHeader(headerRequestID)
		if rid == "" {
			rid = newRequestID()
		}
		c.Request.Header.Set(headerRequestID, rid)
		c.Writer.Header().Set(headerRequestID, rid)

		c.Next() // 继续执行后续 handler（含 ReverseProxy）

		latency := time.Since(start)
		log.Printf("[%s] %s %s rid=%s → %d (%v)",
			c.Request.Method, path, c.ClientIP(), rid, c.Writer.Status(), latency)
	}
}

func newRequestID() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return hex.EncodeToString([]byte(time.Now().Format("150405.000000000")))
	}
	return hex.EncodeToString(b)
}
