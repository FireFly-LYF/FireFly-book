package auth

import (
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	UserID    string `json:"uid,omitempty"` // 业务用户 id；有则网关注入 X-User-Id
	TokenType string `json:"typ,omitempty"` // access；拒绝 refresh 等其它类型当 Bearer
	jwt.RegisteredClaims
}

// Tenant 返回租户（JWT iss），供中间件白名单校验。
func (c *Claims) Tenant() string {
	if c == nil {
		return ""
	}
	return c.Issuer
}

// Sign 签发租户 Token（Admin /gateway/login，已禁用路由）。
func Sign(tenant string, ttl time.Duration, secret []byte) (string, error) {
	return SignUser(tenant, "", ttl, secret)
}

// SignUser 签发业务 Access Token（含 uid）；与 user-service 字段对齐。
func SignUser(tenant, userID string, ttl time.Duration, secret []byte) (string, error) {
	claims := Claims{
		UserID:    userID,
		TokenType: "access",
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    tenant,
			Subject:   userID,
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(secret)
}

func Parse(tokenStr string, secret []byte) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (any, error) {
		return secret, nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, jwt.ErrTokenInvalidClaims
	}
	return claims, nil
}
