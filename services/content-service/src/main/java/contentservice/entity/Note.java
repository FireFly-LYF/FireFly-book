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
    /** WGS84 纬度；空表示未标地点 */
    private Double lat;
    /** WGS84 经度 */
    private Double lng;
    private String placeName;
    private Integer status;
    private Integer likeCount;
    private String idemKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 附近流查询时填充，不入库 */
    private Double distanceKm;
    /** 列表角标用：媒体张数，不入库 */
    private Integer mediaCount;
    /** image | video，不入库（由 note_media / 扩展字段推断） */
    private String mediaKind;

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
    public Double getLat() {
        return lat;
    }
    public void setLat(Double lat) {
        this.lat = lat;
    }
    public Double getLng() {
        return lng;
    }
    public void setLng(Double lng) {
        this.lng = lng;
    }
    public String getPlaceName() {
        return placeName;
    }
    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }
    public Double getDistanceKm() {
        return distanceKm;
    }
    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }
    public Integer getMediaCount() {
        return mediaCount;
    }
    public void setMediaCount(Integer mediaCount) {
        this.mediaCount = mediaCount;
    }
    public String getMediaKind() {
        return mediaKind;
    }
    public void setMediaKind(String mediaKind) {
        this.mediaKind = mediaKind;
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
