package socialservice.cache;

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
 * 用户点赞/收藏的笔记 id 分页：user:liked:{id}:* / user:collected:{id}:*，TTL 10 分钟。
 */
@Component
public class UserNoteIdsCache {

    private static final Logger log = LoggerFactory.getLogger(UserNoteIdsCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public UserNoteIdsCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<Long> getLiked(Long userId, int page, int size) {
        return getIds(likedKey(userId, page, size));
    }

    public void putLiked(Long userId, int page, int size, List<Long> ids) {
        putIds(likedKey(userId, page, size), ids);
    }

    public void evictLiked(Long userId) {
        deletePrefix("user:liked:" + userId + ":");
    }

    public List<Long> getCollected(Long userId, int page, int size) {
        return getIds(collectedKey(userId, page, size));
    }

    public void putCollected(Long userId, int page, int size, List<Long> ids) {
        putIds(collectedKey(userId, page, size), ids);
    }

    public void evictCollected(Long userId) {
        deletePrefix("user:collected:" + userId + ":");
    }

    private List<Long> getIds(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            if (json.isBlank()) {
                return List.of();
            }
            Long[] arr = jsonMapper.readValue(json, Long[].class);
            return arr == null || arr.length == 0 ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("读用户笔记 id 列表缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void putIds(String key, List<Long> ids) {
        try {
            redis.opsForValue().set(
                    key,
                    jsonMapper.writeValueAsString(ids != null ? ids : List.of()),
                    TTL);
        } catch (Exception e) {
            log.warn("写用户笔记 id 列表缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    private void deletePrefix(String prefix) {
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("删用户笔记 id 列表缓存失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    private static String likedKey(Long userId, int page, int size) {
        return "user:liked:" + userId + ":" + page + ":" + size;
    }

    private static String collectedKey(Long userId, int page, int size) {
        return "user:collected:" + userId + ":" + page + ":" + size;
    }
}
