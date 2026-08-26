package feedservice.mq;

/** 与 content NoteIndexEvent 字段对齐 */
public class NoteIndexEvent {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;
    private java.util.List<String> mediaUrls;

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
    public java.util.List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(java.util.List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
}
