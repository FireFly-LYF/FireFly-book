package notifyservice.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 通知分页整包响应：{@code notify:inbox:resp:{userId}:{page}:{size}}。
 * L1 Caffeine（refreshAfterWrite）+ L2 Redis，存 ApiResponse JSON 字节。
 */
@Component
public class NotifyListCache {

    private static final Logger log = LoggerFactory.getLogger(NotifyListCache.class);
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER = Duration.ofSeconds(3);
    private static final Duration EXPIRE_AFTER = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final boolean l1Enabled;
    private final AtomicReference<Function<String, byte[]>> deepLoader = new AtomicReference<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "notify-list-cache-refresh");
        t.setDaemon(true);
        return t;
    });
    private final LoadingCache<String, byte[]> local;

    public NotifyListCache(
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
        log.info("NotifyListCache L1={}", l1Enabled);
    }

    public void bindDeepLoader(Function<String, byte[]> loader) {
        deepLoader.set(loader);
    }

    public byte[] get(Long userId, int page, int size) {
        if (userId == null) {
            return null;
        }
        String k = key(userId, page, size);
        if (!l1Enabled) {
            return load(k);
        }
        return local.get(k);
    }

    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        String prefix = "notify:inbox:resp:" + userId + ":";
        local.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        // 兼容旧 key
        String oldPrefix = "notify:inbox:" + userId + ":";
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            Set<String> oldKeys = redis.keys(oldPrefix + "*");
            if (oldKeys != null && !oldKeys.isEmpty()) {
                redis.delete(oldKeys);
            }
        } catch (Exception e) {
            log.warn("删通知列表缓存失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private byte[] load(String cacheKey) {
        byte[] fromRedis = readRedis(cacheKey);
        if (fromRedis != null) {
            return fromRedis;
        }
        Function<String, byte[]> loader = deepLoader.get();
        if (loader == null) {
            throw new IllegalStateException("notify cache deep loader unset");
        }
        byte[] built = loader.apply(cacheKey);
        if (built == null) {
            throw new IllegalStateException("notify cache deep loader returned null");
        }
        writeRedis(cacheKey, built);
        return built;
    }

    private byte[] readRedis(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读通知列表缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeRedis(String key, byte[] body) {
        try {
            redis.opsForValue().set(key, new String(body, StandardCharsets.UTF_8), REDIS_TTL);
        } catch (Exception e) {
            log.warn("写通知列表缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    public static String key(Long userId, int page, int size) {
        return "notify:inbox:resp:" + userId + ":" + page + ":" + size;
    }

    public static long[] parseKey(String key) {
        if (key == null || !key.startsWith("notify:inbox:resp:")) {
            return null;
        }
        String[] parts = key.split(":");
        // notify inbox resp {userId} {page} {size}
        if (parts.length != 6) {
            return null;
        }
        try {
            return new long[]{
                    Long.parseLong(parts[3]),
                    Long.parseLong(parts[4]),
                    Long.parseLong(parts[5])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
