package socialservice.cache;

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
 * 评论分页响应字节缓存（整包 ApiResponse JSON，免重复序列化）：
 * <ul>
 *   <li>L1 Caffeine：{@code refreshAfterWrite} 异步回源，仍返回旧值</li>
 *   <li>L2 Redis：{@code note:comments:{noteId}:{page}:{size}}</li>
 * </ul>
 */
@Component
public class CommentCache {

    private static final Logger log = LoggerFactory.getLogger(CommentCache.class);
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER = Duration.ofSeconds(3);
    private static final Duration EXPIRE_AFTER = Duration.ofSeconds(30);
    private static final long LOCAL_MAX_SIZE = 10_000;

    private final StringRedisTemplate redis;
    private final boolean l1Enabled;
    private final AtomicReference<Function<String, byte[]>> deepLoader = new AtomicReference<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "comment-cache-refresh");
        t.setDaemon(true);
        return t;
    });
    private final LoadingCache<String, byte[]> local;

    public CommentCache(
            StringRedisTemplate redis,
            @Value("${firefly.cache.l1-enabled:true}") boolean l1Enabled) {
        this.redis = redis;
        this.l1Enabled = l1Enabled;
        this.local = Caffeine.newBuilder()
                .maximumSize(LOCAL_MAX_SIZE)
                .refreshAfterWrite(REFRESH_AFTER)
                .expireAfterWrite(EXPIRE_AFTER)
                .executor(refreshExecutor)
                .build(this::load);
        log.info("CommentCache L1={}", l1Enabled);
    }

    /** 由 CommentService 绑定：Redis miss 时查库并序列化整包 JSON。 */
    public void bindDeepLoader(Function<String, byte[]> loader) {
        deepLoader.set(loader);
    }

    public byte[] get(Long noteId, int page, int size) {
        if (noteId == null) {
            return null;
        }
        String k = key(noteId, page, size);
        if (!l1Enabled) {
            return load(k);
        }
        return local.get(k);
    }

    public void put(Long noteId, int page, int size, byte[] body) {
        if (noteId == null || body == null) {
            return;
        }
        String k = key(noteId, page, size);
        if (l1Enabled) {
            local.put(k, body);
        }
        writeRedis(k, body);
    }

    /** 发评后按笔记前缀淘汰 L1 + L2 各页。 */
    public void evictNote(Long noteId) {
        if (noteId == null) {
            return;
        }
        String prefix = "note:comments:" + noteId + ":";
        local.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("删评论分页缓存失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    /** @deprecated 兼容旧调用，改为 {@link #evictNote(Long)} */
    public void evict(Long noteId) {
        evictNote(noteId);
    }

    private byte[] load(String key) {
        byte[] fromRedis = readRedis(key);
        if (fromRedis != null) {
            return fromRedis;
        }
        Function<String, byte[]> loader = deepLoader.get();
        if (loader == null) {
            throw new IllegalStateException("comment cache deep loader unset");
        }
        byte[] built = loader.apply(key);
        if (built == null) {
            throw new IllegalStateException("comment cache deep loader returned null");
        }
        writeRedis(key, built);
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
            log.warn("读评论分页缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeRedis(String key, byte[] body) {
        try {
            redis.opsForValue().set(key, new String(body, StandardCharsets.UTF_8), REDIS_TTL);
        } catch (Exception e) {
            log.warn("写评论分页缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    public static String key(Long noteId, int page, int size) {
        return "note:comments:" + noteId + ":" + page + ":" + size;
    }

    /** 解析 {@link #key(Long, int, int)}；非法则返回 null。 */
    public static long[] parseKey(String key) {
        if (key == null || !key.startsWith("note:comments:")) {
            return null;
        }
        String[] parts = key.split(":");
        // note comments {id} {page} {size}
        if (parts.length != 5) {
            return null;
        }
        try {
            return new long[]{
                    Long.parseLong(parts[2]),
                    Long.parseLong(parts[3]),
                    Long.parseLong(parts[4])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
