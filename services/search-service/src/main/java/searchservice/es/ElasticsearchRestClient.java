package searchservice.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import searchservice.config.FireflyEsProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 薄封装：用 HTTP REST 访问 Elasticsearch（PUT/_doc、DELETE、_search）。
 * 比 Elasticsearch Java API Client 上手成本低，适合教程项目。
 */
@Component
public class ElasticsearchRestClient {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchRestClient.class);

    private final RestTemplate restTemplate;
    private final FireflyEsProperties props;
    /** Spring Boot 4 使用 Jackson 3（tools.jackson），不要用旧的 com.fasterxml.jackson.databind */
    private final JsonMapper jsonMapper;

    public ElasticsearchRestClient(RestTemplate restTemplate, FireflyEsProperties props, JsonMapper jsonMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    /** 索引不存在则创建（幂等：已存在则忽略） */
    public void ensureIndex(String index, Map<String, Object> mappings) {
        String url = props.getBaseUrl() + "/" + index;
        try {
            restTemplate.getForEntity(url, String.class);
            return; // 已存在
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mappings", mappings);
        // ES 创建索引：PUT /{index}
        restTemplate.put(url, body);
        log.info("已创建 ES 索引 {}", index);
    }

    /** 删除整个索引；不存在也不抛错（用于换 mapping / 重建） */
    public void deleteIndex(String index) {
        String url = props.getBaseUrl() + "/" + index;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
            log.info("已删除 ES 索引 {}", index);
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("删除索引时不存在 index={}", index);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    /** 写入/覆盖文档：PUT /{index}/_doc/{id} */
    public void indexDoc(String index, String id, Object document) {
        String url = props.getBaseUrl() + "/" + index + "/_doc/" + id;
        restTemplate.put(url, document);
    }

    /** 删除文档：DELETE /{index}/_doc/{id}；不存在也不抛错 */
    public void deleteDoc(String index, String id) {
        String url = props.getBaseUrl() + "/" + index + "/_doc/" + id;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("删除时文档不存在 index={} id={}", index, id);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
        }
    }

    /**
     * multi_match 检索（走倒排；依赖索引 IK 分词）。
     * 不做 wildcard / 前导通配，避免大数据量下慢查询。
     */
    public <T> List<T> search(String index, List<String> fields, String q, int from, int size, Class<T> type) {
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", q);
        multiMatch.put("fields", fields);
        multiMatch.put("type", "best_fields");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("size", size);
        body.put("query", Map.of("multi_match", multiMatch));

        String url = props.getBaseUrl() + "/" + index + "/_search";
        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body),
                new ParameterizedTypeReference<String>() {});
        return parseHits(resp.getBody(), type);
    }

    private <T> List<T> parseHits(String json, Class<T> type) {
        List<T> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            JsonNode hits = jsonMapper.readTree(json).path("hits").path("hits");
            if (!hits.isArray()) {
                return list;
            }
            for (JsonNode hit : hits) {
                JsonNode source = hit.get("_source");
                if (source != null && !source.isNull()) {
                    list.add(jsonMapper.treeToValue(source, type));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("解析 ES 搜索结果失败: " + e.getMessage(), e);
        }
        return list;
    }
}
