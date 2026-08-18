package searchservice.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchservice.config.FireflyEsProperties;
import searchservice.document.NoteDocument;
import searchservice.document.UserDocument;
import searchservice.es.ElasticsearchRestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);

    private final ElasticsearchRestClient es;
    private final FireflyEsProperties props;

    public SearchIndexService(ElasticsearchRestClient es, FireflyEsProperties props) {
        this.es = es;
        this.props = props;
    }

    /**
     * 启动时确保索引存在（IK：ik_max_word 索引 / ik_smart 搜索）。
     * 旧索引无 IK mapping 时设 firefly.elasticsearch.recreate-indices=true 重建一次。
     */
    @PostConstruct
    public void initIndices() {
        try {
            if (props.isRecreateIndices()) {
                log.warn("recreate-indices=true：将删除并重建 ES 索引（搜索数据需重新同步）");
                es.deleteIndex(props.getNoteIndex());
                es.deleteIndex(props.getUserIndex());
            }
            es.ensureIndex(props.getNoteIndex(), noteIndexMapping());
            es.ensureIndex(props.getUserIndex(), userIndexMapping());
        } catch (Exception e) {
            // 启动不因 ES 短暂不可用而直接挂掉，方便本地排错
            log.warn("初始化 ES 索引失败（请确认 ES 已启动且已安装 IK）: {}", e.getMessage());
        }
    }

    public void indexNote(NoteDocument doc) {
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("笔记 id 不能为空");
        }
        es.indexDoc(props.getNoteIndex(), String.valueOf(doc.getId()), doc);
        log.info("已索引笔记 id={}", doc.getId());
    }

    public void deleteNote(Long noteId) {
        if (noteId == null) {
            throw new IllegalArgumentException("笔记 id 不能为空");
        }
        es.deleteDoc(props.getNoteIndex(), String.valueOf(noteId));
        log.info("已删除笔记索引 id={}", noteId);
    }

    public void indexUser(UserDocument doc) {
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("用户 id 不能为空");
        }
        es.indexDoc(props.getUserIndex(), String.valueOf(doc.getId()), doc);
        log.info("已索引用户 id={}", doc.getId());
    }

    public List<NoteDocument> searchNotes(String q, int page, int size) {
        String keyword = normalizeKeyword(q);
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 10;
        }
        int from = (page - 1) * size;
        return es.search(props.getNoteIndex(), List.of("title", "content"), keyword, from, size, NoteDocument.class);
    }

    public List<UserDocument> searchUsers(String q) {
        String keyword = normalizeKeyword(q);
        return es.search(props.getUserIndex(), List.of("nickname", "username"), keyword, 0, 20, UserDocument.class);
    }

    /** 非空、限长、禁止以 * / ? 开头（避免前导通配打挂 ES） */
    private String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        String keyword = q.trim();
        int max = props.getMaxQueryLength();
        if (keyword.length() > max) {
            throw new IllegalArgumentException("关键词过长，最多 " + max + " 个字符");
        }
        char first = keyword.charAt(0);
        if (first == '*' || first == '?') {
            throw new IllegalArgumentException("关键词不能以 * 或 ? 开头");
        }
        return keyword;
    }

    private static Map<String, Object> noteIndexMapping() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "long"));
        properties.put("userId", Map.of("type", "long"));
        properties.put("title", ikTextField());
        properties.put("content", ikTextField());
        properties.put("coverUrl", Map.of("type", "keyword", "ignore_above", 512));
        return Map.of("properties", properties);
    }

    private static Map<String, Object> userIndexMapping() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "long"));
        properties.put("username", ikTextField());
        properties.put("nickname", ikTextField());
        properties.put("avatarUrl", Map.of("type", "keyword", "ignore_above", 512));
        return Map.of("properties", properties);
    }

    /** text + IK：入库细切，查询智能切；keyword 子字段仅精确匹配用，不走 wildcard */
    private static Map<String, Object> ikTextField() {
        return Map.of(
                "type", "text",
                "analyzer", "ik_max_word",
                "search_analyzer", "ik_smart",
                "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))
        );
    }
}
