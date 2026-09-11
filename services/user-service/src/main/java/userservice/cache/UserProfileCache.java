package userservice.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import userservice.entity.User;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 用户资料：L1 Caffeine（refreshAfterWrite）+ L2 Redis {@code user:info:{id}}。
 */
@Component
public class UserProfileCache {

    private static final Logger log = LoggerFactory.getLogger(UserProfileCache.class);
    private static final String KEY_PREFIX = "user:info:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER = Duration.ofSeconds(3);
    private static final Duration EXPIRE_AFTER = Duration.ofSeconds(30);

    /** LoadingCache 不能存 null；表示用户不存在。 */
    private static final User ABSENT = new User();

    private final StringRedisTemplate redis;
    private final boolean l1Enabled;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AtomicReference<Function<Long, User>> dbLoader = new AtomicReference<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "user-profile-cache-refresh");
        t.setDaemon(true);
        return t;
    });
    private final LoadingCache<Long, User> local;

    public UserProfileCache(
            StringRedisTemplate redis,
            @Value("${firefly.cache.l1-enabled:true}") boolean l1Enabled) {
        this.redis = redis;
        this.l1Enabled = l1Enabled;
        this.local = Caffeine.newBuilder()
                .maximumSize(10_000)
                .refreshAfterWrite(REFRESH_AFTER)
                .expireAfterWrite(EXPIRE_AFTER)
                .executor(refreshExecutor)
                .build(this::load);
        log.info("UserProfileCache L1={}", l1Enabled);
    }

    public void bindDbLoader(Function<Long, User> loader) {
        dbLoader.set(loader);
    }

    public User get(Long id) {
        if (id == null) {
            return null;
        }
        User u = l1Enabled ? local.get(id) : load(id);
        return u == ABSENT ? null : u;
    }

    public void put(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        try {
            user.setPassword(null);
            if (l1Enabled) {
                local.put(user.getId(), user);
            }
            redis.opsForValue().set(key(user.getId()), jsonMapper.writeValueAsString(user), REDIS_TTL);
        } catch (Exception e) {
            log.warn("写用户缓存失败 id={}: {}", user.getId(), e.getMessage());
        }
    }

    public void evict(Long id) {
        if (id == null) {
            return;
        }
        local.invalidate(id);
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("删用户缓存失败 id={}: {}", id, e.getMessage());
        }
    }

    private User load(Long id) {
        try {
            String json = redis.opsForValue().get(key(id));
            if (json != null && !json.isBlank()) {
                User u = jsonMapper.readValue(json, User.class);
                if (u != null) {
                    u.setPassword(null);
                    return u;
                }
            }
        } catch (Exception e) {
            log.warn("读用户缓存失败 id={}: {}", id, e.getMessage());
        }
        Function<Long, User> loader = dbLoader.get();
        if (loader == null) {
            return ABSENT;
        }
        User fromDb = loader.apply(id);
        if (fromDb == null) {
            return ABSENT;
        }
        fromDb.setPassword(null);
        try {
            redis.opsForValue().set(key(id), jsonMapper.writeValueAsString(fromDb), REDIS_TTL);
        } catch (Exception e) {
            log.warn("回写用户缓存失败 id={}: {}", id, e.getMessage());
        }
        return fromDb;
    }

    private static String key(Long id) {
        return KEY_PREFIX + id;
    }
}
