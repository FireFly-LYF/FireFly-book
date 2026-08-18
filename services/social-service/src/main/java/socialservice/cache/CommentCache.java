package socialservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import socialservice.entity.Comment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 笔记评论列表：note:comments:{noteId}，TTL 10 分钟。
 */
@Component
public class CommentCache {

    private static final Logger log = LoggerFactory.getLogger(CommentCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public CommentCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 未命中返回 null。 */
    public List<Comment> get(Long noteId) {
        if (noteId == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key(noteId));
            if (json == null) {
                return null;
            }
            if (json.isBlank()) {
                return List.of();
            }
            Comment[] arr = jsonMapper.readValue(json, Comment[].class);
            return arr == null || arr.length == 0 ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("读评论缓存失败 noteId={}: {}", noteId, e.getMessage());
            return null;
        }
    }

    public void put(Long noteId, List<Comment> comments) {
        if (noteId == null) {
            return;
        }
        try {
            redis.opsForValue().set(
                    key(noteId),
                    jsonMapper.writeValueAsString(comments != null ? comments : List.of()),
                    TTL);
        } catch (Exception e) {
            log.warn("写评论缓存失败 noteId={}: {}", noteId, e.getMessage());
        }
    }

    public void evict(Long noteId) {
        if (noteId == null) {
            return;
        }
        try {
            redis.delete(key(noteId));
        } catch (Exception e) {
            log.warn("删评论缓存失败 noteId={}: {}", noteId, e.getMessage());
        }
    }

    private static String key(Long noteId) {
        return "note:comments:" + noteId;
    }
}
