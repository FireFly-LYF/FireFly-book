package socialservice.controller;

import socialservice.common.ApiResponse;
import socialservice.service.CollectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/social/collect")
public class CollectController {

    private final CollectService collectService;

    public CollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    /** 某用户收藏的笔记 id 列表 */
    @GetMapping("/of/{userId}")
    public ApiResponse<List<Long>> listByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            return ApiResponse.ok(collectService.listCollectedNoteIds(userId, page, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping("/{noteId}")
    public ApiResponse<Void> collect(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long noteId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            collectService.collect(userId, noteId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> uncollect(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long noteId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            collectService.uncollect(userId, noteId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @GetMapping("/{noteId}/me")
    public ApiResponse<Map<String, Boolean>> me(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long noteId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        return ApiResponse.ok(Map.of("collected", collectService.collectedByMe(userId, noteId)));
    }
}
