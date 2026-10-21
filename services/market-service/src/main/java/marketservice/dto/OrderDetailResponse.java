package marketservice.dto;

import marketservice.entity.AfterSale;
import marketservice.entity.MarketOrder;
import marketservice.entity.OrderItem;
import marketservice.entity.Payment;

import java.time.LocalDateTime;
import java.util.List;

public class OrderDetailResponse {
    private Long id;
    private Long buyerId;
    private Long sellerId;
    private String status;
    private Integer amountCents;
    private String addressSnapshot;
    private LocalDateTime createdAt;
    private List<OrderItem> items;
    private Payment payment;
    private AfterSale afterSale;

    public static OrderDetailResponse from(MarketOrder order, List<OrderItem> items, Payment payment, AfterSale afterSale) {
        OrderDetailResponse r = new OrderDetailResponse();
        r.id = order.getId();
        r.buyerId = order.getBuyerId();
        r.sellerId = order.getSellerId();
        r.status = order.getStatus();
        r.amountCents = order.getAmountCents();
        r.addressSnapshot = order.getAddressSnapshot();
        r.createdAt = order.getCreatedAt();
        r.items = items;
        r.payment = payment;
        r.afterSale = afterSale;
        return r;
    }

    public Long getId() { return id; }
    public Long getBuyerId() { return buyerId; }
    public Long getSellerId() { return sellerId; }
    public String getStatus() { return status; }
    public Integer getAmountCents() { return amountCents; }
    public String getAddressSnapshot() { return addressSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
    public Payment getPayment() { return payment; }
    public AfterSale getAfterSale() { return afterSale; }
}
