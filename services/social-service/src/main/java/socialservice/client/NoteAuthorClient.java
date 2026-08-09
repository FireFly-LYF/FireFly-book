package socialservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class NoteAuthorClient {

    private static final Logger log = LoggerFactory.getLogger(NoteAuthorClient.class);

    private final RestTemplate restTemplate;
    private final String contentBaseUrl;

    public NoteAuthorClient(
            RestTemplate restTemplate,
            @Value("${firefly.content.base-url:http://127.0.0.1:9002}") String contentBaseUrl) {
        this.restTemplate = restTemplate;
        this.contentBaseUrl = contentBaseUrl;
    }

    /** 查笔记作者；失败返回 null，不阻断点赞主流程 */
    public Long findAuthorId(Long noteId) {
        try {
            String url = contentBaseUrl + "/api/note/" + noteId;
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body == null || !Integer.valueOf(0).equals(asInt(body.get("code")))) {
                return null;
            }
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                return null;
            }
            Object noteObj = data.get("note");
            if (!(noteObj instanceof Map<?, ?> note)) {
                return null;
            }
            return asLong(note.get("userId"));
        } catch (Exception e) {
            log.warn("查询笔记作者失败 noteId={}: {}", noteId, e.getMessage());
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
