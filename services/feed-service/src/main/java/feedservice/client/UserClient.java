package feedservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestTemplate restTemplate;
    private final String userBaseUrl;

    public UserClient(
            RestTemplate restTemplate,
            @Value("${firefly.user-base-url:http://127.0.0.1:9001}") String userBaseUrl) {
        this.restTemplate = restTemplate;
        this.userBaseUrl = userBaseUrl;
    }

    /** 读扩散步骤 1：当前用户关注的人 id */
    public List<Long> listFollowingIds(Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(userId));
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userBaseUrl + "/api/user/me/following-ids",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body == null || !Integer.valueOf(0).equals(asInt(body.get("code")))) {
                log.warn("following-ids 失败: {}", body);
                return Collections.emptyList();
            }
            Object data = body.get("data");
            if (!(data instanceof List<?> list)) {
                return Collections.emptyList();
            }
            return list.stream().map(UserClient::asLong).filter(id -> id != null).toList();
        } catch (Exception e) {
            log.warn("调 user following-ids 失败 userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
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
