package socialservice.service;

import socialservice.dto.CreateCommentRequest;
import socialservice.entity.Comment;
import socialservice.mapper.CommentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {

    private final CommentMapper commentMapper;

    public CommentService(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
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
        return commentMapper.findById(comment.getId());
    }

    public List<Comment> listByNoteId(Long noteId) {
        return commentMapper.listByNoteId(noteId);
    }
}
