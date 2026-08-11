package feedservice.client;

import feedservice.dto.FeedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
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

    /** 读扩散步骤 3：拉某作者最近笔记 */
    public List<FeedItem> listByUser(Long userId, int page, int size) {
        try {
            String url = contentBaseUrl + "/api/note/user/" + userId + "?page=" + page + "&size=" + size;
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
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
        } catch (Exception e) {
            log.warn("调 content listByUser 失败 userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
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
