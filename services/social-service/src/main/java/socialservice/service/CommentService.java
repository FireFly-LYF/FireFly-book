package socialservice.service;

import socialservice.client.NoteAuthorClient;
import socialservice.dto.CreateCommentRequest;
import socialservice.entity.Comment;
import socialservice.mapper.CommentMapper;
import socialservice.mq.MqConstants;
import socialservice.mq.NotifyEvent;
import socialservice.mq.NotifyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final NoteAuthorClient noteAuthorClient;
    private final NotifyEventPublisher notifyEventPublisher;

    public CommentService(
            CommentMapper commentMapper,
            NoteAuthorClient noteAuthorClient,
            NotifyEventPublisher notifyEventPublisher) {
        this.commentMapper = commentMapper;
        this.noteAuthorClient = noteAuthorClient;
        this.notifyEventPublisher = notifyEventPublisher;
    }

    public Comment create(Long userId, CreateCommentRequest req) {
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

        Comment comment = new Comment();
        comment.setNoteId(req.getNoteId());
        comment.setUserId(userId);
        comment.setParentId(req.getParentId());
        comment.setContent(content);
        commentMapper.insert(comment);
        Comment saved = commentMapper.findById(comment.getId());
        publishCommentNotify(userId, saved);
        return saved;
    }

    public List<Comment> listByNoteId(Long noteId) {
        return commentMapper.listByNoteId(noteId);
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
}
