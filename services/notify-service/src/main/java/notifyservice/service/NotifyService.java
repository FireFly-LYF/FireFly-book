package notifyservice.service;

import notifyservice.cache.NotifyListCache;
import notifyservice.dto.CreateNotifyRequest;
import notifyservice.dto.ReadNotifyRequest;
import notifyservice.entity.Notification;
import notifyservice.mapper.NotificationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class NotifyService {

    private static final Set<String> ALLOWED_TYPES = Set.of("LIKE", "COMMENT", "FOLLOW");

    private final NotificationMapper notificationMapper;
    private final NotifyListCache notifyListCache;

    public NotifyService(NotificationMapper notificationMapper, NotifyListCache notifyListCache) {
        this.notificationMapper = notificationMapper;
        this.notifyListCache = notifyListCache;
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

    public List<Notification> list(Long userId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        List<Notification> cached = notifyListCache.get(userId, page, size);
        if (cached != null) {
            return cached;
        }
        int offset = (page - 1) * size;
        List<Notification> list = notificationMapper.listByUser(userId, size, offset);
        if (list == null) {
            list = List.of();
        }
        notifyListCache.put(userId, page, size, list);
        return list;
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
