package mediaservice.media;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 媒体访问签名：HMAC-SHA256(path + "|" + exp)，十六进制。
 * path 形如 /files/{uuid}.jpg（无 query）。
 */
public final class MediaUrlSigner {

    private static final Pattern SAFE_FILE =
            Pattern.compile("^/files/[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|gif|webp)$");

    private final byte[] secret;
    private final long ttlSeconds;

    public MediaUrlSigner(String secret, long ttlSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("media.sign-secret must not be empty");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("media.sign-ttl-seconds must be > 0");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public static boolean isSafePath(String path) {
        return path != null && SAFE_FILE.matcher(path).matches();
    }

    public String sign(String path, long expEpochSec) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String payload = path + "|" + expEpochSec;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("media sign failed", e);
        }
    }

    public boolean verify(String path, long expEpochSec, String sigHex) {
        if (!isSafePath(path) || sigHex == null || sigHex.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        if (expEpochSec < now) {
            return false;
        }
        String expected = sign(path, expEpochSec);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sigHex.getBytes(StandardCharsets.UTF_8));
    }

    /** 返回带 ?exp=&sig= 的相对路径，便于经网关访问。 */
    public String signPath(String path) {
        if (!isSafePath(path)) {
            throw new IllegalArgumentException("非法媒体路径");
        }
        long exp = System.currentTimeMillis() / 1000L + ttlSeconds;
        String sig = sign(path, exp);
        return path + "?exp=" + exp + "&sig=" + sig;
    }
}
