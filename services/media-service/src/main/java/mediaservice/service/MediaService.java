package mediaservice.service;

import mediaservice.config.MediaProperties;
import mediaservice.entity.MediaAsset;
import mediaservice.mapper.MediaAssetMapper;
import mediaservice.media.ImageProbe;
import mediaservice.media.MediaUrlSigner;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaAssetMapper mediaAssetMapper;
    private final MediaProperties mediaProperties;
    private final MediaUrlSigner mediaUrlSigner;

    public MediaService(
            MediaAssetMapper mediaAssetMapper,
            MediaProperties mediaProperties,
            MediaUrlSigner mediaUrlSigner) {
        this.mediaAssetMapper = mediaAssetMapper;
        this.mediaProperties = mediaProperties;
        this.mediaUrlSigner = mediaUrlSigner;
    }

    public Map<String, Object> save(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(32);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取文件");
        }

        ImageProbe.DetectedImage detected = ImageProbe.detect(head)
                .orElseThrow(() -> new IllegalArgumentException("只允许上传 JPEG/PNG/GIF/WEBP 图片"));

        String original = file.getOriginalFilename();
        // 客户端扩展名仅作提示，不参与落盘名
        if (original != null && !original.isBlank() && !ImageProbe.isAllowedClientHint(original)) {
            throw new IllegalArgumentException("文件扩展名不在白名单内");
        }

        String filename = UUID.randomUUID() + detected.extension();
        String canonicalPath = "/files/" + filename;

        try {
            Path dest = Paths.get(mediaProperties.getStorageDir(), filename);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest);
        } catch (IOException e) {
            throw new IllegalStateException("保存文件失败: " + e.getMessage(), e);
        }

        MediaAsset asset = new MediaAsset();
        asset.setUserId(userId);
        asset.setOriginalName(original != null ? original : filename);
        asset.setContentType(detected.contentType());
        asset.setSizeBytes(file.getSize());
        // 库内只存规范路径，不含签名（签名会过期）
        asset.setUrl(canonicalPath);
        mediaAssetMapper.insert(asset);

        Map<String, Object> result = new HashMap<>();
        result.put("id", asset.getId());
        result.put("url", canonicalPath);
        result.put("contentType", detected.contentType());
        result.put("accessUrl", mediaUrlSigner.signPath(canonicalPath));
        return result;
    }

    public MediaAsset findById(Long id) {
        return mediaAssetMapper.findById(id);
    }

    /** 把规范路径或历史绝对 URL 转成 /files/... 再签名。 */
    public String signAccessUrl(String rawUrl) {
        String path = canonicalize(rawUrl);
        return mediaUrlSigner.signPath(path);
    }

    public static String canonicalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        String u = rawUrl.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        // 兼容历史：http://127.0.0.1:9003/files/xxx.jpg
        int idx = u.indexOf("/files/");
        if (idx >= 0) {
            u = u.substring(idx);
        }
        if (!u.startsWith("/files/")) {
            throw new IllegalArgumentException("非法媒体路径");
        }
        if (!MediaUrlSigner.isSafePath(u)) {
            throw new IllegalArgumentException("非法媒体路径");
        }
        return u;
    }

    /** 内网直读原图字节（不经签名 URL）。 */
    public MediaFileContent readFileBytes(String rawPath) throws IOException {
        String path = canonicalize(rawPath);
        String filename = path.substring("/files/".length());
        Path root = Paths.get(mediaProperties.getStorageDir()).toAbsolutePath().normalize();
        Path file = root.resolve(filename).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("非法媒体路径");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在");
        }
        byte[] bytes = Files.readAllBytes(file);
        Optional<ImageProbe.DetectedImage> detected = ImageProbe.detect(bytes);
        String contentType = detected.map(ImageProbe.DetectedImage::contentType)
                .orElse(contentTypeFromExtension(path));
        return new MediaFileContent(bytes, contentType);
    }

    private static String contentTypeFromExtension(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public record MediaFileContent(byte[] bytes, String contentType) {}
}
