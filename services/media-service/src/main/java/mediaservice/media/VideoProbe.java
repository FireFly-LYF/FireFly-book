package mediaservice.media;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** 短视频魔数识别（mp4/webm）。 */
public final class VideoProbe {

    public record DetectedVideo(String contentType, String extension) {}

    private VideoProbe() {}

    public static Optional<DetectedVideo> detect(byte[] head) {
        if (head == null || head.length < 12) {
            return Optional.empty();
        }
        if (isMp4(head)) {
            return Optional.of(new DetectedVideo("video/mp4", ".mp4"));
        }
        if (isWebm(head)) {
            return Optional.of(new DetectedVideo("video/webm", ".webm"));
        }
        return Optional.empty();
    }

    /** ISO BMFF: ....ftyp */
    private static boolean isMp4(byte[] b) {
        return b.length >= 8
                && b[4] == 'f'
                && b[5] == 't'
                && b[6] == 'y'
                && b[7] == 'p';
    }

    /** EBML header */
    private static boolean isWebm(byte[] b) {
        return (b[0] & 0xFF) == 0x1A
                && (b[1] & 0xFF) == 0x45
                && (b[2] & 0xFF) == 0xDF
                && (b[3] & 0xFF) == 0xA3;
    }

    public static boolean isAllowedClientHint(String filename) {
        if (filename == null) {
            return true;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return Arrays.asList(".mp4", ".webm", ".mov").stream().anyMatch(lower::endsWith);
    }
}
