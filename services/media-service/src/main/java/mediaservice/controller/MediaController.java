package mediaservice.controller;

import mediaservice.common.ApiResponse;
import mediaservice.entity.MediaAsset;
import mediaservice.service.MediaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam("file") MultipartFile file) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(mediaService.save(userId, file));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(50001, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaAsset> getById(@PathVariable Long id) {
        MediaAsset asset = mediaService.findById(id);
        if (asset == null) {
            return ApiResponse.fail(40401, "媒体不存在");
        }
        return ApiResponse.ok(asset);
    }
}
