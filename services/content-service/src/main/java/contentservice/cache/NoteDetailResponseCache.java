package contentservice.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 笔记详情整包响应缓存：{@code note:detail:resp:{id}}。
 * L1 Caffeine（refreshAfterWrite）+ L2 Redis，存 ApiResponse JSON 字节。
 */
@Component
public class NoteDetailResponseCache {

    private static final Logger log = LoggerFactory.getLogger(NoteDetailResponseCache.class);
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER = Duration.ofSeconds(3);
    private static final Duration EXPIRE_AFTER = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "note:detail:resp:";

    private final StringRedisTemplate redis;
    private final boolean l1Enabled;
    private final AtomicReference<Function<Long, byte[]>> deepLoader = new AtomicReference<>();
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "note-detail-cache-refresh");
        t.setDaemon(true);
        return t;
    });
    private final LoadingCache<Long, byte[]> local;

    public NoteDetailResponseCache(
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
        log.info("NoteDetailResponseCache L1={}", l1Enabled);
    }

    public void bindDeepLoader(Function<Long, byte[]> loader) {
        deepLoader.set(loader);
    }

    public byte[] get(Long noteId) {
        if (noteId == null) {
            return null;
        }
        if (!l1Enabled) {
            return load(noteId);
        }
        return local.get(noteId);
    }

    public void evict(Long noteId) {
        if (noteId == null) {
            return;
        }
        local.invalidate(noteId);
        try {
            redis.delete(key(noteId));
        } catch (Exception e) {
            log.warn("删笔记详情响应缓存失败 id={}: {}", noteId, e.getMessage());
        }
    }

    private byte[] load(Long noteId) {
        byte[] fromRedis = readRedis(noteId);
        if (fromRedis != null) {
            return fromRedis;
        }
        Function<Long, byte[]> loader = deepLoader.get();
        if (loader == null) {
            throw new IllegalStateException("note detail cache deep loader unset");
        }
        byte[] built = loader.apply(noteId);
        if (built == null) {
            throw new IllegalStateException("note detail cache deep loader returned null");
        }
        writeRedis(noteId, built);
        return built;
    }

    private byte[] readRedis(Long noteId) {
        try {
            String json = redis.opsForValue().get(key(noteId));
            if (json == null) {
                return null;
            }
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读笔记详情响应缓存失败 id={}: {}", noteId, e.getMessage());
            return null;
        }
    }

    private void writeRedis(Long noteId, byte[] body) {
        try {
            redis.opsForValue().set(key(noteId), new String(body, StandardCharsets.UTF_8), REDIS_TTL);
        } catch (Exception e) {
            log.warn("写笔记详情响应缓存失败 id={}: {}", noteId, e.getMessage());
        }
    }

    private static String key(Long id) {
        return KEY_PREFIX + id;
    }
}
