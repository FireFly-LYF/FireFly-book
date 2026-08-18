package contentservice.cache;

import contentservice.entity.Note;
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
 * 用户笔记墙 / 最新 N 条：note:list:{userId}:*，TTL 10 分钟。
 * 发帖、改帖、删帖时按用户前缀整段失效。
 */
@Component
public class NoteListCache {

    private static final Logger log = LoggerFactory.getLogger(NoteListCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public NoteListCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 未命中返回 null。 */
    public List<Note> getWall(Long userId, int page, int size) {
        return getNotes(wallKey(userId, page, size));
    }

    public void putWall(Long userId, int page, int size, List<Note> notes) {
        putNotes(wallKey(userId, page, size), notes);
    }

    /** 未命中返回 null。 */
    public List<Note> getLatest(Long userId, int perUser) {
        return getNotes(latestKey(userId, perUser));
    }

    public void putLatest(Long userId, int perUser, List<Note> notes) {
        putNotes(latestKey(userId, perUser), notes);
    }

    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        deletePrefix("note:list:" + userId + ":");
    }

    private List<Note> getNotes(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            if (json.isBlank()) {
                return List.of();
            }
            Note[] arr = jsonMapper.readValue(json, Note[].class);
            return arr == null || arr.length == 0 ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("读笔记列表缓存失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void putNotes(String key, List<Note> notes) {
        try {
            redis.opsForValue().set(
                    key,
                    jsonMapper.writeValueAsString(notes != null ? notes : List.of()),
                    TTL);
        } catch (Exception e) {
            log.warn("写笔记列表缓存失败 key={}: {}", key, e.getMessage());
        }
    }

    private void deletePrefix(String prefix) {
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("删笔记列表缓存失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    private static String wallKey(Long userId, int page, int size) {
        return "note:list:" + userId + ":wall:" + page + ":" + size;
    }

    private static String latestKey(Long userId, int perUser) {
        return "note:list:" + userId + ":latest:" + perUser;
    }
}
