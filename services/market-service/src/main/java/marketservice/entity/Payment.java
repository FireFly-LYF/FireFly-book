package marketservice.entity;

import java.time.LocalDateTime;

public class Payment {
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String CHANNEL_MOCK = "mock";

    private Long id;
    private Long orderId;
    private Integer amountCents;
    private String channel;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
}
