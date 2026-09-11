package socialservice.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import socialservice.cache.UserNoteIdsCache;
import socialservice.client.NoteAuthorClient;
import socialservice.mapper.NoteLikeMapper;
import socialservice.mq.MqConstants;
import socialservice.mq.NotifyEvent;
import socialservice.mq.NotifyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);
    private static final String COUNT_KEY = "note:like:count:";
    private static final Duration COUNT_TTL = Duration.ofHours(24);

    private final NoteLikeMapper noteLikeMapper;
    private final NoteAuthorClient noteAuthorClient;
    private final NotifyEventPublisher notifyEventPublisher;
    private final StringRedisTemplate redis;
    private final UserNoteIdsCache userNoteIdsCache;
    private final boolean l1Enabled;
    private final ExecutorService refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "like-count-cache-refresh");
        t.setDaemon(true);
        return t;
    });
    private final LoadingCache<Long, Long> localCount;

    public LikeService(
            NoteLikeMapper noteLikeMapper,
            NoteAuthorClient noteAuthorClient,
            NotifyEventPublisher notifyEventPublisher,
            StringRedisTemplate redis,
            UserNoteIdsCache userNoteIdsCache,
            @Value("${firefly.cache.l1-enabled:true}") boolean l1Enabled) {
        this.noteLikeMapper = noteLikeMapper;
        this.noteAuthorClient = noteAuthorClient;
        this.notifyEventPublisher = notifyEventPublisher;
        this.redis = redis;
        this.userNoteIdsCache = userNoteIdsCache;
        this.l1Enabled = l1Enabled;
        this.localCount = Caffeine.newBuilder()
                .maximumSize(10_000)
                .refreshAfterWrite(Duration.ofSeconds(3))
                .expireAfterWrite(Duration.ofSeconds(30))
                .executor(refreshExecutor)
                .build(this::loadCount);
        log.info("LikeService count L1={}", l1Enabled);
    }

    public void like(Long userId, Long noteId) {
        try {
            noteLikeMapper.insert(noteId, userId);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("已点过赞");
        }
        userNoteIdsCache.evictLiked(userId);
        incrCount(noteId);
        publishLikeNotify(userId, noteId);
    }

    public void unlike(Long userId, Long noteId) {
        if (noteLikeMapper.delete(noteId, userId) == 0) {
            throw new IllegalArgumentException("尚未点赞");
        }
        userNoteIdsCache.evictLiked(userId);
        decrCount(noteId);
    }

    /** L1 Caffeine + Redis 标量；未命中则 COUNT 库表并回写 */
    public long count(Long noteId) {
        if (noteId == null) {
            return 0L;
        }
        if (!l1Enabled) {
            return loadCount(noteId);
        }
        return localCount.get(noteId);
    }

    public boolean likedByMe(Long userId, Long noteId) {
        return noteLikeMapper.exists(noteId, userId) > 0;
    }

    public List<Long> listLikedNoteIds(Long userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        List<Long> cached = userNoteIdsCache.getLiked(userId, page, size);
        if (cached != null) {
            return cached;
        }
        int offset = (page - 1) * size;
        List<Long> ids = noteLikeMapper.findNoteIdsByUser(userId, offset, size);
        if (ids == null) {
            ids = List.of();
        }
        userNoteIdsCache.putLiked(userId, page, size, ids);
        return ids;
    }

    private long loadCount(Long noteId) {
        String key = countKey(noteId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Long.parseLong(cached);
            }
        } catch (Exception e) {
            log.warn("读点赞计数缓存失败 noteId={}: {}", noteId, e.getMessage());
        }
        return rebuildCount(noteId);
    }

    private void incrCount(Long noteId) {
        String key = countKey(noteId);
        try {
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                Long v = redis.opsForValue().increment(key);
                redis.expire(key, COUNT_TTL);
                if (v != null) {
                    localCount.put(noteId, v);
                } else {
                    localCount.invalidate(noteId);
                }
            } else {
                long n = rebuildCount(noteId);
                localCount.put(noteId, n);
            }
        } catch (Exception e) {
            log.warn("点赞计数写 Redis 失败 noteId={}: {}", noteId, e.getMessage());
            localCount.invalidate(noteId);
        }
    }

    private void decrCount(Long noteId) {
        String key = countKey(noteId);
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(key))) {
                localCount.invalidate(noteId);
                return;
            }
            Long v = redis.opsForValue().decrement(key);
            if (v != null && v < 0) {
                redis.opsForValue().set(key, "0", COUNT_TTL);
                localCount.put(noteId, 0L);
            } else {
                redis.expire(key, COUNT_TTL);
                if (v != null) {
                    localCount.put(noteId, v);
                } else {
                    localCount.invalidate(noteId);
                }
            }
        } catch (Exception e) {
            log.warn("取消赞计数写 Redis 失败 noteId={}: {}", noteId, e.getMessage());
            localCount.invalidate(noteId);
        }
    }

    private long rebuildCount(Long noteId) {
        long n = noteLikeMapper.countByNoteId(noteId);
        try {
            redis.opsForValue().set(countKey(noteId), String.valueOf(n), COUNT_TTL);
        } catch (Exception e) {
            log.warn("回写点赞计数到 Redis 失败 noteId={}: {}", noteId, e.getMessage());
        }
        localCount.put(noteId, n);
        return n;
    }

    private static String countKey(Long noteId) {
        return COUNT_KEY + noteId;
    }

    private void publishLikeNotify(Long fromUserId, Long noteId) {
        Long authorId = noteAuthorClient.findAuthorId(noteId);
        if (authorId == null) {
            log.warn("无法获取笔记作者，跳过点赞通知 noteId={}", noteId);
            return;
        }
        if (authorId.equals(fromUserId)) {
            return;
        }
        NotifyEvent event = new NotifyEvent(
                authorId, fromUserId, "LIKE", noteId, "赞了你的笔记");
        notifyEventPublisher.publish(MqConstants.RK_LIKE_CREATED, event);
    }
}
