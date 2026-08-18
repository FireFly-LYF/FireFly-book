package socialservice.controller;

import socialservice.common.ApiResponse;
import socialservice.dto.CreateCommentRequest;
import socialservice.entity.Comment;
import socialservice.service.CommentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/social/comment")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ApiResponse<Comment> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateCommentRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(commentService.create(userId, req, idempotencyKey));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @GetMapping("/{noteId}")
    public ApiResponse<List<Comment>> list(@PathVariable Long noteId) {
        return ApiResponse.ok(commentService.listByNoteId(noteId));
    }
}
