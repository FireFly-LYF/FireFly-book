package com.firefly.javagateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** 校验 Access Token（与 user-service 签发字段对齐）。 */
public final class JwtSupport {

    public static final String TYP_ACCESS = "access";

    private JwtSupport() {}

    public static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static String parseUserId(String secret, String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object typ = claims.get("typ");
        if (typ != null && !TYP_ACCESS.equals(String.valueOf(typ))) {
            throw new IllegalArgumentException("not access token");
        }
        Object uid = claims.get("uid");
        if (uid != null && !String.valueOf(uid).isBlank()) {
            return String.valueOf(uid);
        }
        return claims.getSubject();
    }
}
