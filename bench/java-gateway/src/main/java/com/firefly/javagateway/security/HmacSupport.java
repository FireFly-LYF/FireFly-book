package com.firefly.javagateway.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** 与 Go/Java 业务侧一致：HMAC-SHA256(userId|unixSec) hex。 */
public final class HmacSupport {

    private HmacSupport() {}

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
}
