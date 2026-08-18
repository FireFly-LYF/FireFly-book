package userservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 关注/粉丝 id 列表：follow:followingIds:{id} / follow:followerIds:{id}，TTL 10 分钟。
 * 资料本身走 {@link UserProfileCache}，避免昵称变更后列表脏数据。
 */
@Component
public class FollowListCache {

    private static final Logger log = LoggerFactory.getLogger(FollowListCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public FollowListCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<Long> getFollowingIds(Long userId) {
        return getIds(followingKey(userId));
    }

    public void putFollowingIds(Long userId, List<Long> ids) {
        putIds(followingKey(userId), ids);
    }

    public void evictFollowing(Long userId) {
        deleteKey(followingKey(userId), userId);
    }

    public List<Long> getFollowerIds(Long userId) {
        return getIds(followerKey(userId));
    }

    public void putFollowerIds(Long userId, List<Long> ids) {
        putIds(followerKey(userId), ids);
    }

    public void evictFollowers(Long userId) {
        deleteKey(followerKey(userId), userId);
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
            log.warn("读关注列表缓存失败 key={}: {}", key, e.getMessage());
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
            log.warn("写关注列表缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    private void deleteKey(String key, Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("删关注列表缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    private static String followingKey(Long userId) {
        return "follow:followingIds:" + userId;
    }

    private static String followerKey(Long userId) {
        return "follow:followerIds:" + userId;
    }
}
