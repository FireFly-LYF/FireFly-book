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

    public static final String TYP_ACCESS = "access";

    private static final Pattern TTL = Pattern.compile("^(\\d+)(d|h|m|s)$", Pattern.CASE_INSENSITIVE);

    private final FireflyProperties.Jwt jwt;
    private final SecretKey key;

    public JwtService(FireflyProperties props) {
        this.jwt = props.getJwt();
        byte[] secret = jwt.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    /** 签发短寿命 Access Token（网关验签用） */
    public String signAccess(Long userId) {
        Instant now = Instant.now();
        String uid = String.valueOf(userId);
        return Jwts.builder()
                .issuer(jwt.getTenant())
                .subject(uid)
                .claim("uid", uid)
                .claim("typ", TYP_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(parseTtl(jwt.resolveAccessTtl()))))
                .signWith(key)
                .compact();
    }

    /** @deprecated 使用 signAccess */
    public String sign(Long userId) {
        return signAccess(userId);
    }

    public Duration accessTtl() {
        return parseTtl(jwt.resolveAccessTtl());
    }

    public Duration refreshTtl() {
        return parseTtl(jwt.getRefreshTtl());
    }

    static Duration parseTtl(String ttl) {
        if (ttl == null || ttl.isBlank()) {
            return Duration.ofMinutes(30);
        }
        Matcher m = TTL.matcher(ttl.trim());
        if (!m.matches()) {
            return Duration.ofMinutes(30);
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2).toLowerCase()) {
            case "d" -> Duration.ofDays(n);
            case "h" -> Duration.ofHours(n);
            case "m" -> Duration.ofMinutes(n);
            case "s" -> Duration.ofSeconds(n);
            default -> Duration.ofMinutes(30);
        };
    }
}
