package middleware

import (
	"path"
	"strings"

	"gateway/internal/auth"
	"gateway/internal/tenant"

	"github.com/gin-gonic/gin"
)

func JWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, true, nil, "")
}

// AdminJWTAuth 运维通道：必须用 admin 密钥验签，且 typ=admin。
func AdminJWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, true, nil, auth.TypAdmin)
}

// OptionalJWTAuth 开发联调：无 Token 放行；有 Token 则校验。
func OptionalJWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, false, nil, "")
}

// JWTAuthExcept 强制 JWT，但 skip 中的「METHOD path」放行（如 POST /api/user/login）。
// path 支持 path.Match 通配（单段 *），例如 GET /api/note/* 。
// 命中 skip 时仍尝试解析 Bearer（可选鉴权），以便登录用户保留 X-User-Id。
func JWTAuthExcept(secret []byte, skip map[string]struct{}) gin.HandlerFunc {
	return jwtAuth(secret, true, skip, "")
}

func matchPublicAPI(skip map[string]struct{}, method, reqPath string) bool {
	if skip == nil {
		return false
	}
	for pattern := range skip {
		m, p, ok := strings.Cut(pattern, " ")
		if !ok || !strings.EqualFold(m, method) {
			continue
		}
		if p == reqPath {
			return true
		}
		if matched, err := path.Match(p, reqPath); err == nil && matched {
			return true
		}
	}
	return false
}

func jwtAuth(secret []byte, required bool, skip map[string]struct{}, requireTyp string) gin.HandlerFunc {
	return func(c *gin.Context) {
		needAuth := required
		if matchPublicAPI(skip, c.Request.Method, c.Request.URL.Path) {
			needAuth = false
		}

		tokenStr, ok := auth.ExtractBearer(c)
		if !ok {
			if needAuth {
				c.JSON(401, gin.H{"code": 401, "msg": "missing token"})
				c.Abort()
				return
			}
			c.Next()
			return
		}
		claims, err := auth.Parse(tokenStr, secret)
		if err != nil {
			c.JSON(401, gin.H{"code": 401, "msg": "invalid token"})
			c.Abort()
			return
		}
		if requireTyp != "" {
			if claims.TokenType != requireTyp {
				c.JSON(401, gin.H{"code": 401, "msg": requireTyp + " token required"})
				c.Abort()
				return
			}
		} else if claims.TokenType != "" && claims.TokenType != auth.TypAccess {
			// 业务链：仅允许 Access；无 typ 的旧 Token 暂兼容；拒绝 admin/refresh 等
			c.JSON(401, gin.H{"code": 401, "msg": "access token required"})
			c.Abort()
			return
		}
		t := claims.Tenant()
		if !tenant.IsValid(t) {
			c.JSON(401, gin.H{"code": 401, "msg": "unknown tenant"})
			c.Abort()
			return
		}
		c.Set("tenant", t)
		if claims.UserID != "" {
			c.Set("userId", claims.UserID)
		} else if claims.Subject != "" {
			c.Set("userId", claims.Subject)
		}
		c.Next()
	}
}
