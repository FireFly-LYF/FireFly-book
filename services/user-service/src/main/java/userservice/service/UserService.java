package userservice.service;

import userservice.entity.User;
import userservice.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User register(String username, String password, String nickname) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User u = new User();
        u.setUsername(username);
        // 极简哈希，生产请用 BCrypt
        u.setPassword(DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8)));
        u.setNickname(nickname);
        userMapper.insert(u);
        u.setPassword(null); // 返回前抹掉密码
        return u;
    }

    /** 登录失败返回 null，由 Controller 转成 40101 */
    public User login(String username, String password) {
        User u = userMapper.findByUsername(username);
        if (u == null) {
            return null;
        }
        String hash = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        if (!hash.equals(u.getPassword())) {
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
        u.setPassword(null);
        return u;
    }
}
