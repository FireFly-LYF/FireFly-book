package userservice.service;

import org.springframework.stereotype.Service;
import userservice.dto.AuthResponse;
import userservice.entity.RefreshToken;
import userservice.entity.User;
import userservice.mapper.RefreshTokenMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuthTokenService {

    private final JwtService jwtService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final UserService userService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(
            JwtService jwtService,
            RefreshTokenMapper refreshTokenMapper,
            UserService userService) {
        this.jwtService = jwtService;
        this.refreshTokenMapper = refreshTokenMapper;
        this.userService = userService;
    }

    public AuthResponse issueTokens(User user, String rawDeviceFingerprint) {
        String deviceFp = requireDeviceFingerprint(rawDeviceFingerprint);
        String access = jwtService.signAccess(user.getId());
        String refresh = createRefreshToken(user.getId(), deviceFp);
        return new AuthResponse(access, refresh, user);
    }

    /**
     * 校验 refresh + 设备指纹 → 轮换 → 签发新双令牌（新 refresh 仍绑定同一指纹）。
     * 指纹不一致视为换设备/盗用 → 作废该 refresh，要求重新登录。
     */
    public AuthResponse refresh(String rawRefresh, String rawDeviceFingerprint) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw new IllegalArgumentException("缺少 refreshToken");
        }
        String deviceFp = requireDeviceFingerprint(rawDeviceFingerprint);
        String hash = hash(rawRefresh);
        RefreshToken row = refreshTokenMapper.findByHash(hash);
        if (row == null) {
            throw new IllegalArgumentException("无效的 refreshToken");
        }
        if (row.getRevoked() != null && row.getRevoked() == 1) {
            refreshTokenMapper.revokeAllByUserId(row.getUserId());
            throw new IllegalArgumentException("refreshToken 已失效，请重新登录");
        }
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenMapper.revokeById(row.getId());
            throw new IllegalArgumentException("refreshToken 已过期，请重新登录");
        }
        if (row.getDeviceFingerprint() == null
                || row.getDeviceFingerprint().isBlank()
                || !row.getDeviceFingerprint().equals(deviceFp)) {
            refreshTokenMapper.revokeById(row.getId());
            throw new IllegalArgumentException("设备不匹配，请重新登录");
        }

        refreshTokenMapper.revokeById(row.getId());
        User user = userService.findById(row.getUserId());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return issueTokens(user, rawDeviceFingerprint);
    }

    public void logout(Long userId) {
        if (userId != null) {
            refreshTokenMapper.revokeAllByUserId(userId);
        }
    }

    private String createRefreshToken(Long userId, String deviceFingerprintHash) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(hash(raw));
        row.setDeviceFingerprint(deviceFingerprintHash);
        row.setExpiresAt(LocalDateTime.now().plus(jwtService.refreshTtl()));
        refreshTokenMapper.insert(row);
        return raw;
    }

    /** 客户端指纹明文 → 入库存 SHA-256 */
    private static String requireDeviceFingerprint(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("缺少设备指纹，请重新登录");
        }
        String trimmed = raw.trim();
        if (trimmed.length() < 8 || trimmed.length() > 256) {
            throw new IllegalArgumentException("设备指纹无效");
        }
        return hash(trimmed);
    }

    private static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
