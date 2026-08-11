package socialservice.service;

import socialservice.client.NoteAuthorClient;
import socialservice.mapper.NoteLikeMapper;
import socialservice.mq.MqConstants;
import socialservice.mq.NotifyEvent;
import socialservice.mq.NotifyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);
    private static final String COUNT_KEY = "note:like:count:";
    private static final Duration COUNT_TTL = Duration.ofHours(24);

    private final NoteLikeMapper noteLikeMapper;
    private final NoteAuthorClient noteAuthorClient;
    private final NotifyEventPublisher notifyEventPublisher;
    private final StringRedisTemplate redis;

    public LikeService(
            NoteLikeMapper noteLikeMapper,
            NoteAuthorClient noteAuthorClient,
            NotifyEventPublisher notifyEventPublisher,
            StringRedisTemplate redis) {
        this.noteLikeMapper = noteLikeMapper;
        this.noteAuthorClient = noteAuthorClient;
        this.notifyEventPublisher = notifyEventPublisher;
        this.redis = redis;
    }

    public void like(Long userId, Long noteId) {
        if (noteLikeMapper.exists(noteId, userId) > 0) {
            throw new IllegalArgumentException("已点过赞");
        }
        // 先写 DB，成功后再改 Redis
        noteLikeMapper.insert(noteId, userId);
        incrCount(noteId);
        publishLikeNotify(userId, noteId);
    }

    public void unlike(Long userId, Long noteId) {
        if (noteLikeMapper.delete(noteId, userId) == 0) {
            throw new IllegalArgumentException("尚未点赞");
        }
        decrCount(noteId);
    }

    /** 优先读 Redis；未命中则 COUNT 库表并回写 */
    public long count(Long noteId) {
        String key = countKey(noteId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                return Long.parseLong(cached);
            } catch (NumberFormatException ignored) {
                // fall through rebuild
            }
        }
        return rebuildCount(noteId);
    }

    public boolean likedByMe(Long userId, Long noteId) {
        return noteLikeMapper.exists(noteId, userId) > 0;
    }

    private void incrCount(Long noteId) {
        String key = countKey(noteId);
        try {
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.opsForValue().increment(key);
                redis.expire(key, COUNT_TTL);
            } else {
                // 无缓存时用 DB 重建（含刚插入的这一条），避免 INCR 从 0 起导致偏少
                rebuildCount(noteId);
            }
        } catch (Exception e) {
            log.warn("点赞计数写 Redis 失败 noteId={}: {}", noteId, e.getMessage());
        }
    }

    private void decrCount(Long noteId) {
        String key = countKey(noteId);
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(key))) {
                return;
            }
            Long v = redis.opsForValue().decrement(key);
            if (v != null && v < 0) {
                redis.opsForValue().set(key, "0", COUNT_TTL);
            } else {
                redis.expire(key, COUNT_TTL);
            }
        } catch (Exception e) {
            log.warn("取消赞计数写 Redis 失败 noteId={}: {}", noteId, e.getMessage());
        }
    }

    private long rebuildCount(Long noteId) {
        long n = noteLikeMapper.countByNoteId(noteId);
        try {
            redis.opsForValue().set(countKey(noteId), String.valueOf(n), COUNT_TTL);
        } catch (Exception e) {
            log.warn("回写点赞计数到 Redis 失败 noteId={}: {}", noteId, e.getMessage());
        }
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
