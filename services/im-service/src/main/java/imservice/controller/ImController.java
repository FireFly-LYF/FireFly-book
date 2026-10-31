package imservice.controller;

import imservice.common.ApiResponse;
import imservice.dto.ConversationView;
import imservice.dto.MessageView;
import imservice.dto.OpenConversationRequest;
import imservice.dto.SendMessageRequest;
import imservice.dto.UnreadCountView;
import imservice.service.ImService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/im")
public class ImController {

    private final ImService imService;

    public ImController(ImService imService) {
        this.imService = imService;
    }

    @PostMapping("/conversations/open")
    public ApiResponse<ConversationView> open(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody OpenConversationRequest req) {
        try {
            return ApiResponse.ok(imService.openDm(userId, req == null ? null : req.getPeerUserId()));
        } catch (IllegalArgumentException e) {
            return fail(e);
        }
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationView>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ApiResponse.ok(imService.listConversations(userId, limit));
        } catch (IllegalArgumentException e) {
            return fail(e);
        }
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<MessageView>> messages(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long conversationId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ApiResponse.ok(imService.listMessages(userId, conversationId, afterId, limit));
        } catch (IllegalArgumentException e) {
            return fail(e);
        }
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<MessageView> send(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            @PathVariable("id") Long conversationId,
            @RequestBody SendMessageRequest req) {
        try {
            return ApiResponse.ok(imService.send(
                    userId, conversationId, req == null ? null : req.getContent(), idemKey));
        } catch (IllegalArgumentException e) {
            return fail(e);
        }
    }

    @PostMapping("/conversations/{id}/read")
    public ApiResponse<Void> read(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long conversationId,
            @RequestBody(required = false) Map<String, Long> body) {
        try {
            Long messageId = body == null ? null : body.get("messageId");
            imService.markRead(userId, conversationId, messageId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return fail(e);
        }
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unread(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        try {
            return ApiResponse.ok(imService.unreadCount(userId));
        } catch (IllegalArgumentException e) {
            return fail(e);
        }
    }

    private static <T> ApiResponse<T> fail(IllegalArgumentException e) {
        String msg = e.getMessage() == null ? "参数错误" : e.getMessage();
        int code = "未登录".equals(msg) ? 40100 : 40001;
        return ApiResponse.fail(code, msg);
    }
}
