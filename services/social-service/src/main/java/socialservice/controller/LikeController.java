package socialservice.controller;

import socialservice.common.ApiResponse;
import socialservice.service.LikeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/social/like")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/{noteId}")
    public ApiResponse<Void> like(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long noteId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            likeService.like(userId, noteId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> unlike(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long noteId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            likeService.unlike(userId, noteId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @GetMapping("/{noteId}/count")
    public ApiResponse<Map<String, Long>> count(@PathVariable Long noteId) {
        return ApiResponse.ok(Map.of("count", likeService.count(noteId)));
    }

    @GetMapping("/{noteId}/me")
    public ApiResponse<Map<String, Boolean>> me(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long noteId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        return ApiResponse.ok(Map.of("liked", likeService.likedByMe(userId, noteId)));
    }
}
