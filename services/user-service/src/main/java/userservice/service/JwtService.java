package userservice.service;

import io.jsonwebtoken.Jwts;
import userservice.config.FireflyProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {

    private static final Pattern TTL = Pattern.compile("^(\\d+)(h|m|s)$", Pattern.CASE_INSENSITIVE);

    private final FireflyProperties.Jwt jwt;
    private final SecretKey key;

    public JwtService(FireflyProperties props) {
        this.jwt = props.getJwt();
        // 与 Go gateway 使用同一原始 secret 字节
        byte[] secret = jwt.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String sign(Long userId) {
        Instant now = Instant.now();
        String uid = String.valueOf(userId);
        return Jwts.builder()
                .issuer(jwt.getTenant())
                .subject(uid)
                .claim("uid", uid)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(parseTtl(jwt.getTtl()))))
                .signWith(key)
                .compact();
    }

    static Duration parseTtl(String ttl) {
        if (ttl == null || ttl.isBlank()) {
            return Duration.ofHours(24);
        }
        Matcher m = TTL.matcher(ttl.trim());
        if (!m.matches()) {
            return Duration.ofHours(24);
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2).toLowerCase()) {
            case "h" -> Duration.ofHours(n);
            case "m" -> Duration.ofMinutes(n);
            case "s" -> Duration.ofSeconds(n);
            default -> Duration.ofHours(24);
        };
    }
}
