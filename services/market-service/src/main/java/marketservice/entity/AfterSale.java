package marketservice.entity;

import java.time.LocalDateTime;

public class AfterSale {
    public static final String TYPE_CANCEL_REFUND = "CANCEL_REFUND";
    public static final String TYPE_REFUND = "REFUND";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_AGREED = "AGREED";
    public static final String STATUS_REJECTED = "REJECTED";

    private Long id;
    private Long orderId;
    private Long buyerId;
    private Long sellerId;
    private String type;
    private String reason;
    private String status;
    private String prevOrderStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrevOrderStatus() { return prevOrderStatus; }
    public void setPrevOrderStatus(String prevOrderStatus) { this.prevOrderStatus = prevOrderStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
