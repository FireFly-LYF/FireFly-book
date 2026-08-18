package contentservice.cache;

import contentservice.entity.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热点笔记：note:info:{id}，TTL 10 分钟。
 * Redis 故障时降级为未命中，不挡主路径。
 */
@Component
public class NoteCache {

    private static final Logger log = LoggerFactory.getLogger(NoteCache.class);
    private static final String KEY_PREFIX = "note:info:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public NoteCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Note get(Long id) {
        if (id == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key(id));
            if (json == null || json.isBlank()) {
                return null;
            }
            return jsonMapper.readValue(json, Note.class);
        } catch (Exception e) {
            log.warn("读笔记缓存失败 id={}: {}", id, e.getMessage());
            return null;
        }
    }

    public Map<Long, Note> getMany(Collection<Long> ids) {
        Map<Long, Note> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        List<Long> ordered = ids.stream().filter(id -> id != null).distinct().toList();
        if (ordered.isEmpty()) {
            return out;
        }
        try {
            List<String> keys = ordered.stream().map(NoteCache::key).toList();
            List<String> values = redis.opsForValue().multiGet(keys);
            if (values == null) {
                return out;
            }
            for (int i = 0; i < ordered.size() && i < values.size(); i++) {
                String json = values.get(i);
                if (json == null || json.isBlank()) {
                    continue;
                }
                Note n = jsonMapper.readValue(json, Note.class);
                if (n != null && n.getId() != null) {
                    out.put(n.getId(), n);
                }
            }
        } catch (Exception e) {
            log.warn("批量读笔记缓存失败: {}", e.getMessage());
        }
        return out;
    }

    public void put(Note note) {
        if (note == null || note.getId() == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(note.getId()), jsonMapper.writeValueAsString(note), TTL);
        } catch (Exception e) {
            log.warn("写笔记缓存失败 id={}: {}", note.getId(), e.getMessage());
        }
    }

    public void putMany(Collection<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        for (Note n : notes) {
            put(n);
        }
    }

    public void evict(Long id) {
        if (id == null) {
            return;
        }
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("删笔记缓存失败 id={}: {}", id, e.getMessage());
        }
    }

    private static String key(Long id) {
        return KEY_PREFIX + id;
    }
}
