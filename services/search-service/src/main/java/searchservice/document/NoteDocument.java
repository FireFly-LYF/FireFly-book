package searchservice.document;

/**
 * 写入 ES notes 索引的文档结构（与教程 12 章示例一致）。
 * MySQL 仍是权威数据源；这里只是「搜索副本」。
 */
public class NoteDocument {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;

    public NoteDocument() {
    }

    public NoteDocument(Long id, Long userId, String title, String content, String coverUrl) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.coverUrl = coverUrl;
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
