package userservice.service;

import userservice.entity.User;
import userservice.mapper.UserMapper;
import userservice.mq.UserIndexEvent;
import userservice.mq.UserIndexEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserIndexEventPublisher userIndexEventPublisher;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, UserIndexEventPublisher userIndexEventPublisher) {
        this.userMapper = userMapper;
        this.userIndexEventPublisher = userIndexEventPublisher;
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
        User u = userMapper.findById(id);
        if (u != null) {
            u.setPassword(null);
        }
        return u;
    }

    /** 只覆盖非空字段，不改 username */
    public User updateProfile(Long userId, String nickname, String avatarUrl, String bio) {
        User u = userMapper.findById(userId);
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
