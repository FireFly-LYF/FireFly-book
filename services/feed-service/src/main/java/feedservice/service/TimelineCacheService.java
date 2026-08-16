package feedservice.service;

import feedservice.entity.FeedInbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Redis ZSet 热缓存：member=noteId，score=创建时间毫秒 */
@Service
public class TimelineCacheService {

    private static final Logger log = LoggerFactory.getLogger(TimelineCacheService.class);
    private static final String KEY_PREFIX = "feed:timeline:";

    private final StringRedisTemplate redis;
    private final int maxLen;

    public TimelineCacheService(
            StringRedisTemplate redis,
            @Value("${firefly.feed.redis-max-len:500}") int maxLen) {
        this.redis = redis;
        this.maxLen = Math.max(50, maxLen);
    }

    public void push(Long userId, Long noteId, LocalDateTime createdAt) {
        if (userId == null || noteId == null) {
            return;
        }
        String key = key(userId);
        double score = toScore(createdAt);
        try {
            redis.opsForZSet().add(key, String.valueOf(noteId), score);
            Long size = redis.opsForZSet().zCard(key);
            if (size != null && size > maxLen) {
                redis.opsForZSet().removeRange(key, 0, size - maxLen - 1);
            }
        } catch (Exception e) {
            log.warn("Redis push 失败 userId={} noteId={}: {}", userId, noteId, e.getMessage());
        }
    }

    public void pushBatch(Long userId, List<FeedInbox> rows) {
        if (userId == null || rows == null || rows.isEmpty()) {
            return;
        }
        String key = key(userId);
        try {
            for (FeedInbox r : rows) {
                if (r.getNoteId() == null) {
                    continue;
                }
                redis.opsForZSet().add(key, String.valueOf(r.getNoteId()), toScore(r.getCreatedAt()));
            }
            Long size = redis.opsForZSet().zCard(key);
            if (size != null && size > maxLen) {
                redis.opsForZSet().removeRange(key, 0, size - maxLen - 1);
            }
        } catch (Exception e) {
            log.warn("Redis pushBatch 失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 最新 noteId 列表；缓存未命中或异常返回 empty（由调用方回源 MySQL） */
    public List<Long> latestNoteIds(Long userId, int limit) {
        if (userId == null || limit < 1) {
            return List.of();
        }
        try {
            Set<String> members = redis.opsForZSet().reverseRange(key(userId), 0, limit - 1);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>(members.size());
            for (String m : members) {
                try {
                    ids.add(Long.parseLong(m));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Redis latestNoteIds 失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public void removeNoteForUsers(List<Long> userIds, Long noteId) {
        if (noteId == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        String member = String.valueOf(noteId);
        try {
            for (Long uid : userIds) {
                if (uid != null) {
                    redis.opsForZSet().remove(key(uid), member);
                }
            }
        } catch (Exception e) {
            log.warn("Redis removeNoteForUsers 失败 noteId={}: {}", noteId, e.getMessage());
        }
    }

    public void removeNotes(Long userId, List<Long> noteIds) {
        if (userId == null || noteIds == null || noteIds.isEmpty()) {
            return;
        }
        try {
            Object[] members = noteIds.stream().map(String::valueOf).toArray();
            redis.opsForZSet().remove(key(userId), members);
        } catch (Exception e) {
            log.warn("Redis removeNotes 失败 userId={}: {}", userId, e.getMessage());
        }
    }

    public void invalidate(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete(key(userId));
        } catch (Exception e) {
            log.warn("Redis invalidate 失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private static double toScore(LocalDateTime t) {
        if (t == null) {
            return System.currentTimeMillis();
        }
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
