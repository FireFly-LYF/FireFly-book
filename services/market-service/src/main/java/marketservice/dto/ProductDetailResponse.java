package marketservice.dto;

import marketservice.entity.Product;

import java.time.LocalDateTime;

public class ProductDetailResponse {
    private Long id;
    private Long sellerId;
    private String title;
    private String description;
    private Integer priceCents;
    private Integer stock;
    private String coverUrl;
    private String status;
    private LocalDateTime createdAt;
    private SellerSummary seller;

    public static ProductDetailResponse from(Product p, SellerSummary seller) {
        ProductDetailResponse r = new ProductDetailResponse();
        r.id = p.getId();
        r.sellerId = p.getSellerId();
        r.title = p.getTitle();
        r.description = p.getDescription();
        r.priceCents = p.getPriceCents();
        r.stock = p.getStock();
        r.coverUrl = p.getCoverUrl();
        r.status = p.getStatus();
        r.createdAt = p.getCreatedAt();
        r.seller = seller;
        return r;
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getPriceCents() { return priceCents; }
    public Integer getStock() { return stock; }
    public String getCoverUrl() { return coverUrl; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public SellerSummary getSeller() { return seller; }

    public static class SellerSummary {
        private Long id;
        private String username;
        private String nickname;
        private String avatarUrl;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }
}
