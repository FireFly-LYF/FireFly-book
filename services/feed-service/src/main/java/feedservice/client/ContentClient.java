package feedservice.client;

import feedservice.dto.FeedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContentClient {

    private static final Logger log = LoggerFactory.getLogger(ContentClient.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestTemplate restTemplate;
    private final String contentBaseUrl;

    public ContentClient(
            RestTemplate restTemplate,
            @Value("${firefly.content-base-url:http://127.0.0.1:9002}") String contentBaseUrl) {
        this.restTemplate = restTemplate;
        this.contentBaseUrl = contentBaseUrl;
    }

    /** 时间线水合：按 id 批量拉笔记（顺序与 ids 一致） */
    public List<FeedItem> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    contentBaseUrl + "/api/note/ids",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseNoteList(resp.getBody());
        } catch (Exception e) {
            log.warn("调 content listByIds 失败 size={}: {}", ids.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 读扩散回退 / 关注回填 */
    public List<FeedItem> listLatestByUsers(List<Long> userIds, int perUser) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userIds", userIds);
            body.put("perUser", perUser);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    contentBaseUrl + "/api/note/users/latest",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseNoteList(resp.getBody());
        } catch (Exception e) {
            log.warn("调 content listLatestByUsers 失败 size={}: {}", userIds.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<FeedItem> listByUser(Long userId, int page, int size) {
        try {
            String url = contentBaseUrl + "/api/note/user/" + userId + "?page=" + page + "&size=" + size;
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseNoteList(resp.getBody());
        } catch (Exception e) {
            log.warn("调 content listByUser 失败 userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<FeedItem> parseNoteList(Map<String, Object> body) {
        if (body == null || !Integer.valueOf(0).equals(asInt(body.get("code")))) {
            return Collections.emptyList();
        }
        Object data = body.get("data");
        if (!(data instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(o -> toItem((Map<?, ?>) o))
                .filter(item -> item.getNoteId() != null)
                .toList();
    }

    private static FeedItem toItem(Map<?, ?> n) {
        FeedItem item = new FeedItem();
        item.setNoteId(asLong(n.get("id")));
        item.setUserId(asLong(n.get("userId")));
        Object title = n.get("title");
        item.setTitle(title != null ? String.valueOf(title) : null);
        Object cover = n.get("coverUrl");
        item.setCoverUrl(cover != null ? String.valueOf(cover) : null);
        item.setCreatedAt(asTime(n.get("createdAt")));
        return item;
    }

    private static LocalDateTime asTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime t) return t;
        String s = String.valueOf(v);
        try {
            if (s.length() > 19) s = s.substring(0, 19);
            return LocalDateTime.parse(s, ISO);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer asInt(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return null;
    }
}
