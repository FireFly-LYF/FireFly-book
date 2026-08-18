package userservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import userservice.entity.User;

import java.time.Duration;

/**
 * 热点用户资料：user:info:{id}，TTL 10 分钟。
 * Redis 故障时降级为未命中，不挡主路径。
 */
@Component
public class UserProfileCache {

    private static final Logger log = LoggerFactory.getLogger(UserProfileCache.class);
    private static final String KEY_PREFIX = "user:info:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public UserProfileCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public User get(Long id) {
        if (id == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key(id));
            if (json == null || json.isBlank()) {
                return null;
            }
            User u = jsonMapper.readValue(json, User.class);
            if (u != null) {
                u.setPassword(null);
            }
            return u;
        } catch (Exception e) {
            log.warn("读用户缓存失败 id={}: {}", id, e.getMessage());
            return null;
        }
    }

    public void put(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        try {
            user.setPassword(null);
            redis.opsForValue().set(key(user.getId()), jsonMapper.writeValueAsString(user), TTL);
        } catch (Exception e) {
            log.warn("写用户缓存失败 id={}: {}", user.getId(), e.getMessage());
        }
    }

    public void evict(Long id) {
        if (id == null) {
            return;
        }
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("删用户缓存失败 id={}: {}", id, e.getMessage());
        }
    }

    private static String key(Long id) {
        return KEY_PREFIX + id;
    }
}
