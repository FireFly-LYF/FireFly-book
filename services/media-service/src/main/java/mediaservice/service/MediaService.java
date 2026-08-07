package mediaservice.service;

import mediaservice.config.MediaProperties;
import mediaservice.entity.MediaAsset;
import mediaservice.mapper.MediaAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaAssetMapper mediaAssetMapper;
    private final MediaProperties mediaProperties;

    public MediaService(MediaAssetMapper mediaAssetMapper, MediaProperties mediaProperties) {
        this.mediaAssetMapper = mediaAssetMapper;
        this.mediaProperties = mediaProperties;
    }

    public Map<String, Object> save(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只允许上传图片");
        }

        String original = file.getOriginalFilename();
        if (original == null || !original.contains(".")) {
            throw new IllegalArgumentException("文件名无效");
        }
        String ext = original.substring(original.lastIndexOf('.'));
        String filename = UUID.randomUUID() + ext;

        try {
            Path dest = Paths.get(mediaProperties.getStorageDir(), filename);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest);
        } catch (IOException e) {
            throw new IllegalStateException("保存文件失败: " + e.getMessage(), e);
        }

        String publicBaseUrl = mediaProperties.getPublicBaseUrl();
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        String url = base + "/" + filename;

        MediaAsset asset = new MediaAsset();
        asset.setUserId(userId);
        asset.setOriginalName(original);
        asset.setContentType(contentType);
        asset.setSizeBytes(file.getSize());
        asset.setUrl(url);
        mediaAssetMapper.insert(asset);

        Map<String, Object> result = new HashMap<>();
        result.put("id", asset.getId());
        result.put("url", url);
        return result;
    }

    public MediaAsset findById(Long id) {
        return mediaAssetMapper.findById(id);
    }
}
