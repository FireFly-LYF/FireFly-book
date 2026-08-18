package socialservice.entity;

import java.time.LocalDateTime;

public class Comment {
    private Long id;
    private Long noteId;
    private Long userId;
    private Long parentId;
    private String content;
    private String idemKey;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getIdemKey() { return idemKey; }
    public void setIdemKey(String idemKey) { this.idemKey = idemKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
