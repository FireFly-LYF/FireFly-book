package socialservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 调 user-service 批量拉用户摘要（评论补全）。 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestTemplate restTemplate;
    private final String userBaseUrl;

    public UserClient(
            RestTemplate restTemplate,
            @Value("${firefly.user.base-url:http://127.0.0.1:9001}") String userBaseUrl) {
        this.restTemplate = restTemplate;
        this.userBaseUrl = userBaseUrl;
    }

    /** userId → 摘要；失败返回空 Map，不阻断评论主路径 */
    public Map<Long, UserSummary> batchSummaries(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> body = Map.of("ids", userIds);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    userBaseUrl + "/api/user/ids",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseSummaries(resp.getBody());
        } catch (Exception e) {
            log.warn("批量查用户摘要失败 size={}: {}", userIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<Long, UserSummary> parseSummaries(Map<String, Object> body) {
        if (body == null || !Integer.valueOf(0).equals(asInt(body.get("code")))) {
            return Collections.emptyMap();
        }
        Object data = body.get("data");
        if (!(data instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserSummary> out = new HashMap<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            Long id = asLong(m.get("id"));
            if (id == null) {
                continue;
            }
            UserSummary s = new UserSummary();
            s.setId(id);
            s.setUsername(asString(m.get("username")));
            s.setNickname(asString(m.get("nickname")));
            s.setAvatarUrl(asString(m.get("avatarUrl")));
            out.put(id, s);
        }
        return out;
    }

    private static Integer asInt(Object v) {
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static final class UserSummary {
        private Long id;
        private String username;
        private String nickname;
        private String avatarUrl;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String displayName() {
            if (nickname != null && !nickname.isBlank()) {
                return nickname;
            }
            if (username != null && !username.isBlank()) {
                return username;
            }
            return id == null ? "用户" : "用户" + id;
        }
    }
}
