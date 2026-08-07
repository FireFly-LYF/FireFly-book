package socialservice.controller;

import socialservice.common.ApiResponse;
import socialservice.service.CollectService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social/collect")
public class CollectController {

    private final CollectService collectService;

    public CollectController(CollectService collectService) {
        this.collectService = collectService;
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
}
