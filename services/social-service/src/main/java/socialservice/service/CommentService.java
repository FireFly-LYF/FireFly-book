package socialservice.service;

import jakarta.annotation.PostConstruct;
import socialservice.cache.CommentCache;
import socialservice.client.NoteAuthorClient;
import socialservice.client.UserClient;
import socialservice.common.ApiResponse;
import socialservice.dto.CommentItem;
import socialservice.dto.CreateCommentRequest;
import socialservice.entity.Comment;
import socialservice.mapper.CommentMapper;
import socialservice.mq.MqConstants;
import socialservice.mq.NotifyEvent;
import socialservice.mq.NotifyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final NoteAuthorClient noteAuthorClient;
    private final UserClient userClient;
    private final NotifyEventPublisher notifyEventPublisher;
    private final CommentCache commentCache;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public CommentService(
            CommentMapper commentMapper,
            NoteAuthorClient noteAuthorClient,
            UserClient userClient,
            NotifyEventPublisher notifyEventPublisher,
            CommentCache commentCache) {
        this.commentMapper = commentMapper;
        this.noteAuthorClient = noteAuthorClient;
        this.userClient = userClient;
        this.notifyEventPublisher = notifyEventPublisher;
        this.commentCache = commentCache;
    }

    @PostConstruct
    void bindCacheLoader() {
        commentCache.bindDeepLoader(this::loadPageResponseBytes);
    }

    public Comment create(Long userId, CreateCommentRequest req, String idempotencyKey) {
        if (req.getNoteId() == null) {
            throw new IllegalArgumentException("noteId 不能为空");
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("评论内容不能空白");
        }
        if (content.length() > 512) {
            throw new IllegalArgumentException("评论内容不能超过 512 字");
        }
        String idemKey = normalizeIdempotencyKey(idempotencyKey);
        if (idemKey != null) {
            Comment existed = commentMapper.findByUserAndIdemKey(userId, idemKey);
            if (existed != null) {
                return existed;
            }
        }

        ParentResolve parent = resolveParent(req.getNoteId(), req.getParentId());

        Comment comment = new Comment();
        comment.setNoteId(req.getNoteId());
        comment.setUserId(userId);
        comment.setParentId(parent.rootParentId());
        comment.setReplyToUserId(parent.replyToUserId());
        comment.setContent(content);
        comment.setIdemKey(idemKey);
        try {
            commentMapper.insert(comment);
        } catch (DuplicateKeyException e) {
            Comment existed = commentMapper.findByUserAndIdemKey(userId, idemKey);
            if (existed != null) {
                return existed;
            }
            throw e;
        }
        commentCache.evictNote(req.getNoteId());
        Comment saved = commentMapper.findById(comment.getId());
        publishCommentNotify(userId, saved);
        return saved;
    }

    /**
     * 仅两级：一级 parent_id=null；二级挂在一级下。
     * 若回复的是二级评论，则挂到其一级，并记录 replyToUserId 用于 @。
     */
    private ParentResolve resolveParent(Long noteId, Long parentId) {
        if (parentId == null) {
            return ParentResolve.topLevel();
        }
        Comment parent = commentMapper.findById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("回复的评论不存在");
        }
        if (!noteId.equals(parent.getNoteId())) {
            throw new IllegalArgumentException("不能回复其它笔记下的评论");
        }
        if (parent.getParentId() == null) {
            return new ParentResolve(parent.getId(), parent.getUserId());
        }
        Comment root = commentMapper.findById(parent.getParentId());
        if (root == null || !noteId.equals(root.getNoteId()) || root.getParentId() != null) {
            throw new IllegalArgumentException("回复的评论无效");
        }
        return new ParentResolve(root.getId(), parent.getUserId());
    }

    /** 返回整包 ApiResponse JSON 字节（可直接写出 HTTP body）。 */
    public byte[] listResponseJson(Long noteId, int page, int size) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId 不能为空");
        }
        int p = normalizePage(page);
        int s = normalizeSize(size);
        return commentCache.get(noteId, p, s);
    }

    public byte[] errorResponseJson(int code, String message) {
        try {
            return jsonMapper.writeValueAsBytes(ApiResponse.fail(code, message));
        } catch (Exception e) {
            return "{\"code\":50000,\"message\":\"error\",\"data\":null}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private byte[] loadPageResponseBytes(String cacheKey) {
        long[] parts = CommentCache.parseKey(cacheKey);
        if (parts == null) {
            throw new IllegalArgumentException("bad comment cache key: " + cacheKey);
        }
        long noteId = parts[0];
        int page = (int) parts[1];
        int size = (int) parts[2];
        int offset = (page - 1) * size;
        List<Comment> rows = commentMapper.listByNoteId(noteId, size, offset);
        List<CommentItem> items = toItems(rows);
        enrichUsers(items);
        try {
            return jsonMapper.writeValueAsBytes(ApiResponse.ok(items));
        } catch (Exception e) {
            throw new IllegalStateException("serialize comment page failed", e);
        }
    }

    private void enrichUsers(List<CommentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CommentItem item : items) {
            if (item.getUserId() != null) {
                ids.add(item.getUserId());
            }
            if (item.getReplyToUserId() != null) {
                ids.add(item.getReplyToUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, UserClient.UserSummary> users = userClient.batchSummaries(List.copyOf(ids));
        for (CommentItem item : items) {
            UserClient.UserSummary u = users.get(item.getUserId());
            if (u != null) {
                item.setNickname(u.displayName());
                item.setAvatarUrl(u.getAvatarUrl());
            }
            if (item.getReplyToUserId() != null) {
                UserClient.UserSummary replyTo = users.get(item.getReplyToUserId());
                if (replyTo != null) {
                    item.setReplyToNickname(replyTo.displayName());
                }
            }
        }
    }

    private static List<CommentItem> toItems(List<Comment> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<CommentItem> items = new ArrayList<>(rows.size());
        for (Comment c : rows) {
            CommentItem item = new CommentItem();
            item.setId(c.getId());
            item.setNoteId(c.getNoteId());
            item.setUserId(c.getUserId());
            item.setParentId(c.getParentId());
            item.setReplyToUserId(c.getReplyToUserId());
            item.setContent(c.getContent());
            item.setCreatedAt(c.getCreatedAt());
            items.add(item);
        }
        return items;
    }

    private static int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private void publishCommentNotify(Long fromUserId, Comment comment) {
        Long notifyUserId;
        String prefix;
        if (comment.getParentId() != null && comment.getReplyToUserId() != null) {
            notifyUserId = comment.getReplyToUserId();
            prefix = "回复了你的评论: ";
        } else {
            notifyUserId = noteAuthorClient.findAuthorId(comment.getNoteId());
            prefix = "评论了你的笔记: ";
            if (notifyUserId == null) {
                log.warn("无法获取笔记作者，跳过评论通知 noteId={}", comment.getNoteId());
                return;
            }
        }
        if (notifyUserId.equals(fromUserId)) {
            return;
        }
        String preview = comment.getContent();
        if (preview != null && preview.length() > 50) {
            preview = preview.substring(0, 50) + "...";
        }
        NotifyEvent event = new NotifyEvent(
                notifyUserId, fromUserId, "COMMENT", comment.getId(), prefix + preview);
        notifyEventPublisher.publish(MqConstants.RK_COMMENT_CREATED, event);
    }

    static String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key 最长 64 字符");
        }
        return key;
    }

    private record ParentResolve(Long rootParentId, Long replyToUserId) {
        static ParentResolve topLevel() {
            return new ParentResolve(null, null);
        }
    }
}
