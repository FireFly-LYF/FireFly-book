package contentservice.mq;

/** 发往 search 的笔记索引事件（字段与 ES 文档对齐） */
public class NoteIndexEvent {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;

    public NoteIndexEvent() {
    }

    public static NoteIndexEvent from(Long id, Long userId, String title, String content, String coverUrl) {
        NoteIndexEvent e = new NoteIndexEvent();
        e.id = id;
        e.userId = userId;
        e.title = title;
        e.content = content;
        e.coverUrl = coverUrl;
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
}
