package socialservice.service;

import socialservice.cache.CommentCache;
import socialservice.client.NoteAuthorClient;
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

import java.util.List;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final NoteAuthorClient noteAuthorClient;
    private final NotifyEventPublisher notifyEventPublisher;
    private final CommentCache commentCache;

    public CommentService(
            CommentMapper commentMapper,
            NoteAuthorClient noteAuthorClient,
            NotifyEventPublisher notifyEventPublisher,
            CommentCache commentCache) {
        this.commentMapper = commentMapper;
        this.noteAuthorClient = noteAuthorClient;
        this.notifyEventPublisher = notifyEventPublisher;
        this.commentCache = commentCache;
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

        Comment comment = new Comment();
        comment.setNoteId(req.getNoteId());
        comment.setUserId(userId);
        comment.setParentId(req.getParentId());
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
        commentCache.evict(req.getNoteId());
        Comment saved = commentMapper.findById(comment.getId());
        publishCommentNotify(userId, saved);
        return saved;
    }

    public List<Comment> listByNoteId(Long noteId) {
        List<Comment> cached = commentCache.get(noteId);
        if (cached != null) {
            return cached;
        }
        List<Comment> list = commentMapper.listByNoteId(noteId);
        if (list == null) {
            list = List.of();
        }
        commentCache.put(noteId, list);
        return list;
    }

    private void publishCommentNotify(Long fromUserId, Comment comment) {
        Long authorId = noteAuthorClient.findAuthorId(comment.getNoteId());
        if (authorId == null) {
            log.warn("无法获取笔记作者，跳过评论通知 noteId={}", comment.getNoteId());
            return;
        }
        if (authorId.equals(fromUserId)) {
            return;
        }
        String preview = comment.getContent();
        if (preview != null && preview.length() > 50) {
            preview = preview.substring(0, 50) + "...";
        }
        NotifyEvent event = new NotifyEvent(
                authorId, fromUserId, "COMMENT", comment.getId(), "评论了你的笔记: " + preview);
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
}
