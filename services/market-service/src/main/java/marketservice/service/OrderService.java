package marketservice.service;

import marketservice.dto.AfterSaleRequest;
import marketservice.dto.CreateOrderRequest;
import marketservice.dto.OrderDetailResponse;
import marketservice.entity.Address;
import marketservice.entity.AfterSale;
import marketservice.entity.CartItem;
import marketservice.entity.MarketOrder;
import marketservice.entity.OrderItem;
import marketservice.entity.Payment;
import marketservice.entity.Product;
import marketservice.mapper.AfterSaleMapper;
import marketservice.mapper.CartItemMapper;
import marketservice.mapper.MarketOrderMapper;
import marketservice.mapper.OrderItemMapper;
import marketservice.mapper.PaymentMapper;
import marketservice.mapper.ProductMapper;
import marketservice.mq.MqConstants;
import marketservice.mq.NotifyPayload;
import marketservice.mq.OutboxRelay;
import marketservice.mq.OutboxService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final MarketOrderMapper marketOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final AfterSaleMapper afterSaleMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;
    private final AddressService addressService;
    private final ProductService productService;
    private final OutboxService outboxService;
    private final OutboxRelay outboxRelay;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OrderService(
            MarketOrderMapper marketOrderMapper,
            OrderItemMapper orderItemMapper,
            PaymentMapper paymentMapper,
            AfterSaleMapper afterSaleMapper,
            ProductMapper productMapper,
            CartItemMapper cartItemMapper,
            AddressService addressService,
            ProductService productService,
            OutboxService outboxService,
            OutboxRelay outboxRelay) {
        this.marketOrderMapper = marketOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.paymentMapper = paymentMapper;
        this.afterSaleMapper = afterSaleMapper;
        this.productMapper = productMapper;
        this.cartItemMapper = cartItemMapper;
        this.addressService = addressService;
        this.productService = productService;
        this.outboxService = outboxService;
        this.outboxRelay = outboxRelay;
    }

    @Transactional
    public OrderDetailResponse create(Long buyerId, CreateOrderRequest req, String idempotencyKey) {
        requireLogin(buyerId);
        if (req == null || req.getAddressId() == null) {
            throw new IllegalArgumentException("addressId 不能为空");
        }
        String idemKey = IdempotencyKeys.normalize(idempotencyKey);
        if (idemKey != null) {
            MarketOrder existed = marketOrderMapper.findByBuyerAndIdemKey(buyerId, idemKey);
            if (existed != null) {
                return detail(buyerId, existed.getId());
            }
        }

        Address address = addressService.requireOwned(buyerId, req.getAddressId());
        List<Line> lines = resolveLines(buyerId, req);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("订单商品不能为空");
        }
        Long sellerId = lines.get(0).product.getSellerId();
        for (Line line : lines) {
            if (!sellerId.equals(line.product.getSellerId())) {
                throw new IllegalArgumentException("同一订单仅支持同一卖家的商品");
            }
            if (buyerId.equals(line.product.getSellerId())) {
                throw new IllegalArgumentException("不能购买自己的商品");
            }
        }

        int amount = 0;
        for (Line line : lines) {
            int n = productMapper.decreaseStock(line.product.getId(), line.quantity);
            if (n == 0) {
                throw new IllegalArgumentException("库存不足：" + line.product.getTitle());
            }
            productService.evictCache(line.product.getId());
            amount += line.product.getPriceCents() * line.quantity;
        }

        MarketOrder order = new MarketOrder();
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setStatus(MarketOrder.STATUS_CREATED);
        order.setAmountCents(amount);
        order.setAddressSnapshot(toAddressJson(address));
        order.setIdemKey(idemKey);
        try {
            marketOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发重试：回补本事务已扣库存，返回首次订单
            for (Line line : lines) {
                productMapper.increaseStock(line.product.getId(), line.quantity);
                productService.evictCache(line.product.getId());
            }
            MarketOrder existed = marketOrderMapper.findByBuyerAndIdemKey(buyerId, idemKey);
            if (existed != null) {
                return detail(buyerId, existed.getId());
            }
            throw e;
        }

        for (Line line : lines) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.product.getId());
            item.setTitle(line.product.getTitle());
            item.setCoverUrl(line.product.getCoverUrl());
            item.setPriceCents(line.product.getPriceCents());
            item.setQuantity(line.quantity);
            orderItemMapper.insert(item);
        }

        if (Boolean.TRUE.equals(req.getFromCart())) {
            List<Long> productIds = lines.stream().map(l -> l.product.getId()).toList();
            cartItemMapper.deleteByProducts(buyerId, productIds);
        }

        return detailInternal(order.getId());
    }

    @Transactional
    public OrderDetailResponse pay(Long buyerId, Long orderId) {
        MarketOrder order = requireBuyerOrder(buyerId, orderId);
        if (MarketOrder.STATUS_PAID.equals(order.getStatus())) {
            return detailInternal(orderId);
        }
        if (!MarketOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前状态不可支付");
        }
        int n = marketOrderMapper.updateStatus(orderId, MarketOrder.STATUS_CREATED, MarketOrder.STATUS_PAID);
        if (n == 0) {
            throw new IllegalArgumentException("支付失败，请重试");
        }
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmountCents(order.getAmountCents());
        payment.setChannel(Payment.CHANNEL_MOCK);
        payment.setStatus(Payment.STATUS_PAID);
        payment.setPaidAt(LocalDateTime.now());
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            // already paid
        }
        enqueueNotify(MqConstants.RK_ORDER_PAID, order.getSellerId(), buyerId, orderId, "ORDER_PAID", "买家已付款");
        kickOutboxAfterCommit();
        return detailInternal(orderId);
    }

    @Transactional
    public OrderDetailResponse cancel(Long buyerId, Long orderId) {
        MarketOrder order = requireBuyerOrder(buyerId, orderId);
        if (!MarketOrder.STATUS_CREATED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅待支付订单可直接取消");
        }
        int n = marketOrderMapper.updateStatus(orderId, MarketOrder.STATUS_CREATED, MarketOrder.STATUS_CANCELLED);
        if (n == 0) {
            throw new IllegalArgumentException("取消失败");
        }
        restoreStock(orderId);
        enqueueNotify(MqConstants.RK_ORDER_CANCELLED, order.getSellerId(), buyerId, orderId,
                "ORDER_CANCELLED", "买家取消了订单");
        kickOutboxAfterCommit();
        return detailInternal(orderId);
    }

    @Transactional
    public OrderDetailResponse ship(Long sellerId, Long orderId) {
        MarketOrder order = requireSellerOrder(sellerId, orderId);
        if (!MarketOrder.STATUS_PAID.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已付款订单可发货");
        }
        int n = marketOrderMapper.updateStatus(orderId, MarketOrder.STATUS_PAID, MarketOrder.STATUS_SHIPPED);
        if (n == 0) {
            throw new IllegalArgumentException("发货失败");
        }
        return detailInternal(orderId);
    }

    @Transactional
    public OrderDetailResponse confirm(Long buyerId, Long orderId) {
        MarketOrder order = requireBuyerOrder(buyerId, orderId);
        if (!MarketOrder.STATUS_SHIPPED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已发货订单可确认收货");
        }
        int n = marketOrderMapper.updateStatus(orderId, MarketOrder.STATUS_SHIPPED, MarketOrder.STATUS_COMPLETED);
        if (n == 0) {
            throw new IllegalArgumentException("确认收货失败");
        }
        return detailInternal(orderId);
    }

    @Transactional
    public AfterSale applyAfterSale(Long buyerId, Long orderId, AfterSaleRequest req) {
        MarketOrder order = requireBuyerOrder(buyerId, orderId);
        if (afterSaleMapper.findPendingByOrderId(orderId) != null) {
            throw new IllegalArgumentException("已有处理中的售后");
        }
        String type;
        String prev = order.getStatus();
        if (MarketOrder.STATUS_PAID.equals(prev)) {
            type = AfterSale.TYPE_CANCEL_REFUND;
        } else if (MarketOrder.STATUS_SHIPPED.equals(prev) || MarketOrder.STATUS_COMPLETED.equals(prev)) {
            type = AfterSale.TYPE_REFUND;
        } else {
            throw new IllegalArgumentException("当前状态不可申请售后");
        }
        int n = marketOrderMapper.updateStatus(orderId, prev, MarketOrder.STATUS_REFUNDING);
        if (n == 0) {
            throw new IllegalArgumentException("申请售后失败");
        }
        AfterSale as = new AfterSale();
        as.setOrderId(orderId);
        as.setBuyerId(buyerId);
        as.setSellerId(order.getSellerId());
        as.setType(type);
        as.setReason(req != null && req.getReason() != null ? trimReason(req.getReason()) : null);
        as.setStatus(AfterSale.STATUS_PENDING);
        as.setPrevOrderStatus(prev);
        afterSaleMapper.insert(as);
        return afterSaleMapper.findById(as.getId());
    }

    @Transactional
    public OrderDetailResponse agreeAfterSale(Long sellerId, Long afterSaleId) {
        AfterSale as = requireSellerAfterSale(sellerId, afterSaleId);
        int n = afterSaleMapper.updateStatus(afterSaleId, AfterSale.STATUS_AGREED);
        if (n == 0) {
            throw new IllegalArgumentException("售后已处理");
        }
        MarketOrder order = marketOrderMapper.findById(as.getOrderId());
        if (order == null || !MarketOrder.STATUS_REFUNDING.equals(order.getStatus())) {
            throw new IllegalArgumentException("订单状态异常");
        }
        int u = marketOrderMapper.updateStatus(order.getId(), MarketOrder.STATUS_REFUNDING, MarketOrder.STATUS_REFUNDED);
        if (u == 0) {
            throw new IllegalArgumentException("退款失败");
        }
        paymentMapper.markRefunded(order.getId(), Payment.STATUS_REFUNDED);
        if (AfterSale.TYPE_CANCEL_REFUND.equals(as.getType())
                || MarketOrder.STATUS_PAID.equals(as.getPrevOrderStatus())) {
            restoreStock(order.getId());
        }
        enqueueNotify(MqConstants.RK_ORDER_REFUNDED, order.getBuyerId(), sellerId, order.getId(),
                "ORDER_REFUNDED", "卖家已同意退款");
        kickOutboxAfterCommit();
        return detailInternal(order.getId());
    }

    @Transactional
    public OrderDetailResponse rejectAfterSale(Long sellerId, Long afterSaleId) {
        AfterSale as = requireSellerAfterSale(sellerId, afterSaleId);
        int n = afterSaleMapper.updateStatus(afterSaleId, AfterSale.STATUS_REJECTED);
        if (n == 0) {
            throw new IllegalArgumentException("售后已处理");
        }
        int u = marketOrderMapper.updateStatus(as.getOrderId(), MarketOrder.STATUS_REFUNDING, as.getPrevOrderStatus());
        if (u == 0) {
            throw new IllegalArgumentException("恢复订单状态失败");
        }
        return detailInternal(as.getOrderId());
    }

    public OrderDetailResponse detail(Long userId, Long orderId) {
        requireLogin(userId);
        MarketOrder order = marketOrderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new IllegalArgumentException("无权查看该订单");
        }
        return detailInternal(orderId);
    }

    public List<OrderDetailResponse> listBuyer(Long buyerId, int page, int size) {
        requireLogin(buyerId);
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 50);
        return toDetails(marketOrderMapper.listByBuyer(buyerId, (p - 1) * s, s));
    }

    public List<OrderDetailResponse> listSeller(Long sellerId, int page, int size) {
        requireLogin(sellerId);
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 50);
        return toDetails(marketOrderMapper.listBySeller(sellerId, (p - 1) * s, s));
    }

    private List<OrderDetailResponse> toDetails(List<MarketOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(MarketOrder::getId).toList();
        List<OrderItem> allItems = orderItemMapper.listByOrderIds(ids);
        Map<Long, List<OrderItem>> byOrder = new HashMap<>();
        for (OrderItem item : allItems) {
            byOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }
        List<OrderDetailResponse> out = new ArrayList<>();
        for (MarketOrder order : orders) {
            out.add(OrderDetailResponse.from(
                    order,
                    byOrder.getOrDefault(order.getId(), List.of()),
                    paymentMapper.findByOrderId(order.getId()),
                    afterSaleMapper.findPendingByOrderId(order.getId())));
        }
        return out;
    }

    private OrderDetailResponse detailInternal(Long orderId) {
        MarketOrder order = marketOrderMapper.findById(orderId);
        return OrderDetailResponse.from(
                order,
                orderItemMapper.listByOrderId(orderId),
                paymentMapper.findByOrderId(orderId),
                afterSaleMapper.findPendingByOrderId(orderId));
    }

    private List<Line> resolveLines(Long buyerId, CreateOrderRequest req) {
        Map<Long, Integer> qtyByProduct = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(req.getFromCart())) {
            List<CartItem> cart = cartItemMapper.listByUser(buyerId);
            if (cart.isEmpty()) {
                throw new IllegalArgumentException("购物车为空");
            }
            for (CartItem c : cart) {
                qtyByProduct.merge(c.getProductId(), c.getQuantity(), Integer::sum);
            }
        } else {
            if (req.getItems() == null || req.getItems().isEmpty()) {
                throw new IllegalArgumentException("items 不能为空");
            }
            for (CreateOrderRequest.OrderItemRequest item : req.getItems()) {
                if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                    throw new IllegalArgumentException("商品与数量不合法");
                }
                qtyByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            }
        }
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtyByProduct.entrySet()) {
            Product p = productService.requireOnSale(e.getKey());
            if (e.getValue() > 99) {
                throw new IllegalArgumentException("单品数量不能超过 99");
            }
            lines.add(new Line(p, e.getValue()));
        }
        return lines;
    }

    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId);
        for (OrderItem item : items) {
            productMapper.increaseStock(item.getProductId(), item.getQuantity());
            productService.evictCache(item.getProductId());
        }
    }

    private void enqueueNotify(String rk, Long userId, Long fromUserId, Long orderId, String type, String content) {
        if (userId == null || fromUserId == null || userId.equals(fromUserId)) {
            return;
        }
        NotifyPayload payload = new NotifyPayload();
        payload.setUserId(userId);
        payload.setFromUserId(fromUserId);
        payload.setType(type);
        payload.setRefId(orderId);
        payload.setContent(content);
        outboxService.enqueueOrderNotify(rk, orderId, payload);
    }

    private void kickOutboxAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            outboxRelay.relayBatch();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                outboxRelay.relayBatch();
            }
        });
    }

    private MarketOrder requireBuyerOrder(Long buyerId, Long orderId) {
        requireLogin(buyerId);
        MarketOrder order = marketOrderMapper.findById(orderId);
        if (order == null || !buyerId.equals(order.getBuyerId())) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private MarketOrder requireSellerOrder(Long sellerId, Long orderId) {
        requireLogin(sellerId);
        MarketOrder order = marketOrderMapper.findById(orderId);
        if (order == null || !sellerId.equals(order.getSellerId())) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private AfterSale requireSellerAfterSale(Long sellerId, Long afterSaleId) {
        requireLogin(sellerId);
        AfterSale as = afterSaleMapper.findById(afterSaleId);
        if (as == null || !sellerId.equals(as.getSellerId())) {
            throw new IllegalArgumentException("售后单不存在");
        }
        if (!AfterSale.STATUS_PENDING.equals(as.getStatus())) {
            throw new IllegalArgumentException("售后已处理");
        }
        return as;
    }

    private String toAddressJson(Address address) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", address.getId());
            m.put("receiverName", address.getReceiverName());
            m.put("phone", address.getPhone());
            m.put("province", address.getProvince());
            m.put("city", address.getCity());
            m.put("district", address.getDistrict());
            m.put("detail", address.getDetail());
            return jsonMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("地址快照序列化失败", e);
        }
    }

    private static String trimReason(String reason) {
        String t = reason.trim();
        if (t.length() > 256) {
            throw new IllegalArgumentException("原因最长 256 字");
        }
        return t;
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
    }

    private static final class Line {
        final Product product;
        final int quantity;

        Line(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }
}
