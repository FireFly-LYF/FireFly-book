package auth

import (
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	Tenant string `json:"iss"`
	UserID string `json:"uid,omitempty"` // 业务用户 id；有则网关注入 X-User-Id
	jwt.RegisteredClaims
	//嵌入后，Claims 自动拥有 JWT 标准字段
}

func Sign(tenant string, ttl time.Duration, secret []byte) (string, error) {
	claims := Claims{
		Tenant: tenant,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    tenant,
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)), //过期时间
			IssuedAt:  jwt.NewNumericDate(time.Now()),          //签发时间
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims) //创建 token
	return token.SignedString(secret)
}

func Parse(tokenStr string, secret []byte) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (any, error) {
		return secret, nil
	})
	if err != nil {
		return nil, err
	}
	//jwt.ParseWithClaims 解析完后，token.Claims 的类型是 jwt.Claims 接口，不是具体的 *Claims
	//把 token.Claims 从「通用类型」转成我们自定义的 Claims
	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		return nil, jwt.ErrTokenInvalidClaims
	}
	return claims, nil
	// claims.Tenant == "tenant-a"
	// claims.ExpiresAt 可读过期时间
}
