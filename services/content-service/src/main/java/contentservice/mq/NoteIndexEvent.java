package contentservice.mq;

import java.util.List;

/** 发往 search / feed / AI 的笔记索引事件（字段与 ES 文档对齐） */
public class NoteIndexEvent {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;
    /** note_media 表中的图片路径列表（规范路径 /files/...） */
    private List<String> mediaUrls;

    public NoteIndexEvent() {
    }

    public static NoteIndexEvent from(
            Long id, Long userId, String title, String content, String coverUrl) {
        return from(id, userId, title, content, coverUrl, null);
    }

    public static NoteIndexEvent from(
            Long id,
            Long userId,
            String title,
            String content,
            String coverUrl,
            List<String> mediaUrls) {
        NoteIndexEvent e = new NoteIndexEvent();
        e.id = id;
        e.userId = userId;
        e.title = title;
        e.content = content;
        e.coverUrl = coverUrl;
        e.mediaUrls = mediaUrls;
        return e;
    }

    public static NoteIndexEvent deleted(Long id) {
        return deleted(id, null);
    }

    public static NoteIndexEvent deleted(Long id, Long userId) {
        NoteIndexEvent e = new NoteIndexEvent();
        e.id = id;
        e.userId = userId;
        return e;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
}
