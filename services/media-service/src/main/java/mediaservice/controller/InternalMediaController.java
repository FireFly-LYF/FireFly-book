package mediaservice.controller;

import mediaservice.service.MediaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 内网专用：AI 审核等服务直读磁盘原图，不经签名 URL。
 */
@RestController
@RequestMapping("/api/internal/files")
public class InternalMediaController {

    private final MediaService mediaService;

    public InternalMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public ResponseEntity<byte[]> read(@RequestParam("path") String path) {
        try {
            MediaService.MediaFileContent file = mediaService.readFileBytes(path);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(file.bytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
