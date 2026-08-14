package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strconv"
)

// InternalPayload 下游身份证明的明文：userId|unixSeconds（userId 可为空）。
func InternalPayload(userID string, unixSec int64) string {
	return userID + "|" + strconv.FormatInt(unixSec, 10)
}

// SignInternal HMAC-SHA256(hex)，供网关注入 / 下游校验。
func SignInternal(secret []byte, userID string, unixSec int64) string {
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(InternalPayload(userID, unixSec)))
	return hex.EncodeToString(mac.Sum(nil))
}

// VerifyInternal 常量时间比较签名。
func VerifyInternal(secret []byte, userID, signHex string, unixSec int64) bool {
	if len(secret) == 0 || signHex == "" {
		return false
	}
	expected := SignInternal(secret, userID, unixSec)
	return hmac.Equal([]byte(expected), []byte(signHex))
}

// FormatTs 写出 X-Gateway-Ts。
func FormatTs(unixSec int64) string {
	return strconv.FormatInt(unixSec, 10)
}

// MustParseTs 解析时间戳；非法返回错误。
func MustParseTs(raw string) (int64, error) {
	if raw == "" {
		return 0, fmt.Errorf("empty ts")
	}
	return strconv.ParseInt(raw, 10, 64)
}
