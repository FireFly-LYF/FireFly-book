package notifyservice.controller;

import notifyservice.common.ApiResponse;
import notifyservice.dto.CreateNotifyRequest;
import notifyservice.dto.ReadNotifyRequest;
import notifyservice.entity.Notification;
import notifyservice.service.NotifyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notify")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Notification>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        return ApiResponse.ok(notifyService.list(userId, page, size));
    }

    @PostMapping("/read")
    public ApiResponse<Map<String, Integer>> read(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody ReadNotifyRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            int updated = notifyService.markRead(userId, req);
            return ApiResponse.ok(Map.of("updated", updated));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    /** 内部接口：供其他服务创建通知；本机暂不鉴权 */
    @PostMapping("/inner/create")
    public ApiResponse<Notification> create(@RequestBody CreateNotifyRequest req) {
        try {
            return ApiResponse.ok(notifyService.create(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }
}
