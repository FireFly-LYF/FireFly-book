package marketservice.dto;

import java.util.List;

public class CreateOrderRequest {
    private Long addressId;
    private Boolean fromCart;
    private List<OrderItemRequest> items;

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
    public Boolean getFromCart() { return fromCart; }
    public void setFromCart(Boolean fromCart) { this.fromCart = fromCart; }
    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }

    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
