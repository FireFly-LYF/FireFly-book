package notifyservice.cache;

import notifyservice.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 通知分页：notify:inbox:{userId}:{page}:{size}，TTL 10 分钟。
 * 新建或标已读后按用户前缀整段失效。
 */
@Component
public class NotifyListCache {

    private static final Logger log = LoggerFactory.getLogger(NotifyListCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public NotifyListCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 未命中返回 null。 */
    public List<Notification> get(Long userId, int page, int size) {
        if (userId == null) {
            return null;
        }
        String key = key(userId, page, size);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            if (json.isBlank()) {
                return List.of();
            }
            Notification[] arr = jsonMapper.readValue(json, Notification[].class);
            return arr == null || arr.length == 0 ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("读通知列表缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public void put(Long userId, int page, int size, List<Notification> list) {
        if (userId == null) {
            return;
        }
        String key = key(userId, page, size);
        try {
            redis.opsForValue().set(
                    key,
                    jsonMapper.writeValueAsString(list != null ? list : List.of()),
                    TTL);
        } catch (Exception e) {
            log.warn("写通知列表缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        String prefix = "notify:inbox:" + userId + ":";
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("删通知列表缓存失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    private static String key(Long userId, int page, int size) {
        return "notify:inbox:" + userId + ":" + page + ":" + size;
    }
}
