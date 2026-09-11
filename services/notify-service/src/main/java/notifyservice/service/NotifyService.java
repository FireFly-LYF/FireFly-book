package notifyservice.service;

import jakarta.annotation.PostConstruct;
import notifyservice.cache.NotifyListCache;
import notifyservice.common.ApiResponse;
import notifyservice.dto.CreateNotifyRequest;
import notifyservice.dto.ReadNotifyRequest;
import notifyservice.entity.Notification;
import notifyservice.mapper.NotificationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

@Service
public class NotifyService {

    private static final Set<String> ALLOWED_TYPES = Set.of("LIKE", "COMMENT", "FOLLOW");

    private final NotificationMapper notificationMapper;
    private final NotifyListCache notifyListCache;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public NotifyService(NotificationMapper notificationMapper, NotifyListCache notifyListCache) {
        this.notificationMapper = notificationMapper;
        this.notifyListCache = notifyListCache;
    }

    @PostConstruct
    void bindCacheLoader() {
        notifyListCache.bindDeepLoader(this::loadPageResponseBytes);
    }

    public Notification create(CreateNotifyRequest req) {
        if (req.getUserId() == null || req.getFromUserId() == null) {
            throw new IllegalArgumentException("userId / fromUserId 不能为空");
        }
        if (req.getType() == null || req.getType().isBlank()) {
            throw new IllegalArgumentException("type 不能为空");
        }
        String type = req.getType().trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("type 仅支持 LIKE/COMMENT/FOLLOW");
        }
        if (req.getUserId().equals(req.getFromUserId())) {
            throw new IllegalArgumentException("不能给自己发通知");
        }
        String content = req.getContent();
        if (content != null && content.length() > 256) {
            throw new IllegalArgumentException("content 不能超过 256 字");
        }

        Notification n = new Notification();
        n.setUserId(req.getUserId());
        n.setFromUserId(req.getFromUserId());
        n.setType(type);
        n.setRefId(req.getRefId() == null ? 0L : req.getRefId());
        n.setContent(content);
        try {
            notificationMapper.insert(n);
        } catch (DuplicateKeyException e) {
            Notification existing = notificationMapper.findByEvent(
                    n.getUserId(), n.getType(), n.getFromUserId(), n.getRefId());
            return existing != null ? existing : n;
        }
        notifyListCache.evictUser(req.getUserId());
        return notificationMapper.findById(n.getId());
    }

    public byte[] listResponseJson(Long userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 100);
        return notifyListCache.get(userId, p, s);
    }

    private byte[] loadPageResponseBytes(String cacheKey) {
        long[] parts = NotifyListCache.parseKey(cacheKey);
        if (parts == null) {
            throw new IllegalArgumentException("bad notify cache key: " + cacheKey);
        }
        long userId = parts[0];
        int page = (int) parts[1];
        int size = (int) parts[2];
        int offset = (page - 1) * size;
        List<Notification> list = notificationMapper.listByUser(userId, size, offset);
        if (list == null) {
            list = List.of();
        }
        try {
            return jsonMapper.writeValueAsBytes(ApiResponse.ok(list));
        } catch (Exception e) {
            throw new IllegalStateException("serialize notify page failed", e);
        }
    }

    public byte[] errorResponseJson(int code, String message) {
        try {
            return jsonMapper.writeValueAsBytes(ApiResponse.fail(code, message));
        } catch (Exception e) {
            return "{\"code\":50000,\"message\":\"error\",\"data\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public int markRead(Long userId, ReadNotifyRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        int updated;
        if (Boolean.TRUE.equals(req.getAll())) {
            updated = notificationMapper.markAllRead(userId);
        } else {
            List<Long> ids = req.getIds();
            if (ids == null || ids.isEmpty()) {
                throw new IllegalArgumentException("请传 all=true 或 ids");
            }
            updated = notificationMapper.markReadByIds(userId, ids);
        }
        notifyListCache.evictUser(userId);
        return updated;
    }
}
