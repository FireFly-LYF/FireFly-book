package com.firefly.internalauth;

import org.springframework.http.HttpHeaders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 网关 / 服务间共用的 HMAC(userId|unixSec) 工具。 */
public final class GatewayHmacSupport {

    private GatewayHmacSupport() {}

    public static String sign(String secret, String userId, long unixSec) {
        String uid = userId == null ? "" : userId;
        String payload = uid + "|" + unixSec;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC sign failed", e);
        }
    }

    public static boolean verify(String secret, String userId, String signHex, long unixSec) {
        if (secret == null || secret.isBlank() || signHex == null || signHex.isBlank()) {
            return false;
        }
        String expected = sign(secret, userId, unixSec);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signHex.getBytes(StandardCharsets.UTF_8));
    }

    public static void applyHeaders(HttpHeaders headers, String secret, String userId) {
        String uid = userId == null ? "" : userId;
        long ts = java.time.Instant.now().getEpochSecond();
        if (!uid.isEmpty()) {
            headers.set("X-User-Id", uid);
        } else {
            headers.remove("X-User-Id");
        }
        headers.set("X-Gateway-Ts", Long.toString(ts));
        headers.set("X-Gateway-Sign", sign(secret, uid, ts));
    }
}
