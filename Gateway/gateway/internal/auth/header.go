package auth

import (
	"strings"

	"github.com/gin-gonic/gin"
)

func ExtractBearer(c *gin.Context) (string, bool) {
	h := c.GetHeader("Authorization")
	if !strings.HasPrefix(h, "Bearer ") {
		return "", false
	}
	token := strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
	return token, token != ""
}

/*
原始请求头：
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx

第一步：获取 Authorization 头
       → "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx"

第二步：检查是否以 "Bearer " 开头
       → true

第三步：去掉 "Bearer " 前缀（注意有空格）
       → "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxx"

第四步：得到纯 Token，用于验证
       → 最终拿到 JWT
*/
