package mediaservice.media;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 按文件魔数识别图片类型；不信任客户端 Content-Type / 扩展名。
 */
public final class ImageProbe {

    public record DetectedImage(String contentType, String extension) {}

    private ImageProbe() {}

    public static Optional<DetectedImage> detect(byte[] head) {
        if (head == null || head.length < 12) {
            return Optional.empty();
        }
        if (isJpeg(head)) {
            return Optional.of(new DetectedImage("image/jpeg", ".jpg"));
        }
        if (isPng(head)) {
            return Optional.of(new DetectedImage("image/png", ".png"));
        }
        if (isGif(head)) {
            return Optional.of(new DetectedImage("image/gif", ".gif"));
        }
        if (isWebp(head)) {
            return Optional.of(new DetectedImage("image/webp", ".webp"));
        }
        return Optional.empty();
    }

    private static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 0x50
                && b[2] == 0x4E
                && b[3] == 0x47
                && b[4] == 0x0D
                && b[5] == 0x0A
                && b[6] == 0x1A
                && b[7] == 0x0A;
    }

    private static boolean isGif(byte[] b) {
        if (b.length < 6) {
            return false;
        }
        String tag = new String(b, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
        return "GIF87a".equals(tag) || "GIF89a".equals(tag);
    }

    private static boolean isWebp(byte[] b) {
        if (b.length < 12) {
            return false;
        }
        // RIFF....WEBP
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    /** 仅用于日志/提示；检测本身不依赖扩展名。 */
    public static boolean isAllowedClientHint(String filename) {
        if (filename == null) {
            return true;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp").stream()
                .anyMatch(lower::endsWith);
    }
}
