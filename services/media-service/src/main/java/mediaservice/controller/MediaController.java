package mediaservice.controller;

import mediaservice.common.ApiResponse;
import mediaservice.entity.MediaAsset;
import mediaservice.service.MediaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /** 为规范路径签发短期 accessUrl，供 &lt;img&gt; 使用。 */
    @PostMapping("/sign")
    public ApiResponse<Map<String, Object>> sign(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody SignRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            List<String> paths = req == null || req.getPaths() == null ? List.of() : req.getPaths();
            if (paths.isEmpty() && req != null && req.getPath() != null) {
                paths = List.of(req.getPath());
            }
            if (paths.isEmpty()) {
                return ApiResponse.fail(40001, "paths 不能为空");
            }
            Map<String, String> signed = new HashMap<>();
            for (String p : paths) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                String canonical = MediaService.canonicalize(p);
                signed.put(canonical, mediaService.signAccessUrl(canonical));
            }
            Map<String, Object> data = new HashMap<>();
            data.put("urls", signed);
            if (signed.size() == 1) {
                String only = signed.values().iterator().next();
                data.put("accessUrl", only);
            }
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        MediaAsset asset = mediaService.findById(id);
        if (asset == null) {
            return ApiResponse.fail(40401, "媒体不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", asset.getId());
        data.put("url", asset.getUrl());
        data.put("contentType", asset.getContentType());
        data.put("accessUrl", mediaService.signAccessUrl(asset.getUrl()));
        return ApiResponse.ok(data);
    }

    public static class SignRequest {
        private String path;
        private List<String> paths = new ArrayList<>();

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }
}
