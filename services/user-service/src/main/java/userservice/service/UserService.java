package userservice.service;

import userservice.cache.UserProfileCache;
import userservice.dto.UserSummary;
import userservice.entity.User;
import userservice.mapper.UserMapper;
import userservice.mq.UserIndexEvent;
import userservice.mq.UserIndexEventPublisher;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserIndexEventPublisher userIndexEventPublisher;
    private final UserProfileCache userProfileCache;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(
            UserMapper userMapper,
            UserIndexEventPublisher userIndexEventPublisher,
            UserProfileCache userProfileCache) {
        this.userMapper = userMapper;
        this.userIndexEventPublisher = userIndexEventPublisher;
        this.userProfileCache = userProfileCache;
    }

    @PostConstruct
    void bindProfileCache() {
        userProfileCache.bindDbLoader(id -> {
            User u = userMapper.findById(id);
            if (u != null) {
                u.setPassword(null);
            }
            return u;
        });
    }

    public User register(String username, String password, String nickname) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setNickname(nickname);
        userMapper.insert(u);

        // 注册成功 → MQ → search 写 users 索引，之后可按昵称搜到
        userIndexEventPublisher.publishUpserted(
                UserIndexEvent.from(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl()));

        u.setPassword(null); // 返回前抹掉密码
        return u;
    }

    /** 登录失败返回 null，由 Controller 转成 40101 */
    public User login(String username, String password) {
        User u = userMapper.findByUsername(username);
        if (u == null) {
            return null;
        }
        String stored = u.getPassword();
        if (isBcrypt(stored)) {
            if (!passwordEncoder.matches(password, stored)) {
                return null;
            }
        } else if (matchesLegacyMd5(password, stored)) {
            // 存量 MD5 校验通过后升级为 BCrypt，下次不再走 MD5
            userMapper.updatePassword(u.getId(), passwordEncoder.encode(password));
        } else {
            return null;
        }
        u.setPassword(null);
        return u;
    }

    public User findById(Long id) {
        return userProfileCache.get(id);
    }

    /**
     * 批量用户摘要：先读单用户缓存，未命中再一次 IN 查询；返回顺序与请求去重后的 id 一致。
     */
    public List<UserSummary> findSummariesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                unique.add(id);
            }
            if (unique.size() >= 100) {
                break;
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }

        Map<Long, User> found = new LinkedHashMap<>();
        List<Long> misses = new ArrayList<>();
        for (Long id : unique) {
            User cached = userProfileCache.get(id);
            if (cached != null) {
                found.put(id, cached);
            } else {
                misses.add(id);
            }
        }
        if (!misses.isEmpty()) {
            List<User> rows = userMapper.findByIds(misses);
            if (rows != null) {
                for (User u : rows) {
                    if (u == null || u.getId() == null) {
                        continue;
                    }
                    u.setPassword(null);
                    userProfileCache.put(u);
                    found.put(u.getId(), u);
                }
            }
        }

        List<UserSummary> out = new ArrayList<>(unique.size());
        for (Long id : unique) {
            User u = found.get(id);
            if (u != null) {
                out.add(toSummary(u));
            }
        }
        return out;
    }

    private static UserSummary toSummary(User u) {
        UserSummary s = new UserSummary();
        s.setId(u.getId());
        s.setUsername(u.getUsername());
        s.setNickname(u.getNickname());
        s.setAvatarUrl(u.getAvatarUrl());
        return s;
    }

    /** 只覆盖非空字段，不改 username */
    public User updateProfile(Long userId, String nickname, String avatarUrl, String bio) {
        User u = findById(userId);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (nickname != null) {
            u.setNickname(nickname);
        }
        if (avatarUrl != null) {
            u.setAvatarUrl(avatarUrl);
        }
        if (bio != null) {
            u.setBio(bio);
        }
        userMapper.updateProfile(u);
        userProfileCache.evict(userId);

        // 昵称/头像变更后覆盖写 ES
        userIndexEventPublisher.publishUpserted(
                UserIndexEvent.from(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl()));

        u.setPassword(null);
        return u;
    }

    private static boolean isBcrypt(String hash) {
        return hash != null
                && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }

    private static boolean matchesLegacyMd5(String rawPassword, String storedHex) {
        if (storedHex == null || storedHex.length() != 32) {
            return false;
        }
        String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        return md5.equalsIgnoreCase(storedHex);
    }
}
