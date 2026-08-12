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

    /** 启动时确保索引存在，并给 title/content 挂 keyword 子字段供 wildcard */
    @PostConstruct
    public void initIndices() {
        try {
            es.ensureIndex(props.getNoteIndex(), textFieldsMapping("title", "content", "coverUrl"));
            es.ensureIndex(props.getUserIndex(), textFieldsMapping("username", "nickname", "avatarUrl"));
        } catch (Exception e) {
            // 启动不因 ES 短暂不可用而直接挂掉，方便本地排错
            log.warn("初始化 ES 索引失败（请确认 ES 已启动）: {}", e.getMessage());
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
        requireKeyword(q);
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 10;
        }
        int from = (page - 1) * size;
        return es.search(props.getNoteIndex(), List.of("title", "content"), q.trim(), from, size, NoteDocument.class);
    }

    public List<UserDocument> searchUsers(String q) {
        requireKeyword(q);
        return es.search(props.getUserIndex(), List.of("nickname", "username"), q.trim(), 0, 20, UserDocument.class);
    }

    private static void requireKeyword(String q) {
        if (q == null || q.isBlank()) {
            throw new IllegalArgumentException("关键词不能为空");
        }
    }

    /** text + keyword 子字段：全文用 multi_match，中文包含用 wildcard */
    private static Map<String, Object> textFieldsMapping(String... fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        // id / userId 用 long，精确过滤时有用
        properties.put("id", Map.of("type", "long"));
        properties.put("userId", Map.of("type", "long"));
        Map<String, Object> keywordSub = Map.of("type", "keyword", "ignore_above", 256);
        Map<String, Object> textWithKeyword = Map.of(
                "type", "text",
                "fields", Map.of("keyword", keywordSub)
        );
        for (String f : fields) {
            properties.put(f, textWithKeyword);
        }
        return Map.of("properties", properties);
    }
}
