package marketservice.service;

import marketservice.dto.CartAddRequest;
import marketservice.dto.CartItemResponse;
import marketservice.dto.CartUpdateRequest;
import marketservice.entity.CartItem;
import marketservice.entity.Product;
import marketservice.mapper.CartItemMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductService productService;

    public CartService(CartItemMapper cartItemMapper, ProductService productService) {
        this.cartItemMapper = cartItemMapper;
        this.productService = productService;
    }

    public List<CartItemResponse> list(Long userId) {
        requireLogin(userId);
        List<CartItem> items = cartItemMapper.listByUser(userId);
        List<CartItemResponse> out = new ArrayList<>();
        for (CartItem item : items) {
            Product p = productService.getCached(item.getProductId());
            CartItemResponse r = new CartItemResponse();
            r.setProductId(item.getProductId());
            r.setQuantity(item.getQuantity());
            if (p != null) {
                r.setTitle(p.getTitle());
                r.setCoverUrl(p.getCoverUrl());
                r.setPriceCents(p.getPriceCents());
                r.setStock(p.getStock());
                r.setStatus(p.getStatus());
                r.setSellerId(p.getSellerId());
            }
            out.add(r);
        }
        return out;
    }

    @Transactional
    public List<CartItemResponse> add(Long userId, CartAddRequest req) {
        requireLogin(userId);
        if (req == null || req.getProductId() == null) {
            throw new IllegalArgumentException("productId 不能为空");
        }
        int qty = req.getQuantity() == null ? 1 : req.getQuantity();
        if (qty < 1 || qty > 99) {
            throw new IllegalArgumentException("数量范围 1~99");
        }
        Product p = productService.requireOnSale(req.getProductId());
        if (userId.equals(p.getSellerId())) {
            throw new IllegalArgumentException("不能购买自己的商品");
        }
        CartItem existing = cartItemMapper.find(userId, req.getProductId());
        if (existing != null) {
            int next = Math.min(99, existing.getQuantity() + qty);
            cartItemMapper.updateQuantity(userId, req.getProductId(), next);
        } else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(req.getProductId());
            item.setQuantity(qty);
            try {
                cartItemMapper.insert(item);
            } catch (DuplicateKeyException e) {
                CartItem again = cartItemMapper.find(userId, req.getProductId());
                if (again != null) {
                    cartItemMapper.updateQuantity(userId, req.getProductId(),
                            Math.min(99, again.getQuantity() + qty));
                }
            }
        }
        return list(userId);
    }

    @Transactional
    public List<CartItemResponse> update(Long userId, Long productId, CartUpdateRequest req) {
        requireLogin(userId);
        if (req == null || req.getQuantity() == null || req.getQuantity() < 1 || req.getQuantity() > 99) {
            throw new IllegalArgumentException("数量范围 1~99");
        }
        if (cartItemMapper.find(userId, productId) == null) {
            throw new IllegalArgumentException("购物车中无该商品");
        }
        cartItemMapper.updateQuantity(userId, productId, req.getQuantity());
        return list(userId);
    }

    @Transactional
    public List<CartItemResponse> remove(Long userId, Long productId) {
        requireLogin(userId);
        cartItemMapper.delete(userId, productId);
        return list(userId);
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
    }
}
