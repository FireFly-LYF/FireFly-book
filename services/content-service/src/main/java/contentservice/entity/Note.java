package contentservice.entity;

import java.time.LocalDateTime;

public class Note {
    /** 已发布（公开展示 / 可进 Feed·搜索） */
    public static final int STATUS_PUBLISHED = 1;
    /** 审核中（先审后发，对外不可见） */
    public static final int STATUS_PENDING = 2;
    /** 审核拒绝 */
    public static final int STATUS_REJECTED = 3;

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;
    private Integer status;
    private Integer likeCount;
    private String idemKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isPublished() {
        return status != null && status == STATUS_PUBLISHED;
    }

    public boolean isPending() {
        return status != null && status == STATUS_PENDING;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getCoverUrl() {
        return coverUrl;
    }
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    public Integer getStatus() {
        return status;
    }
    public void setStatus(Integer status) {
        this.status = status;
    }
    public Integer getLikeCount() {
        return likeCount;
    }
    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }
    public String getIdemKey() {
        return idemKey;
    }
    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
