package marketservice.controller;

import marketservice.common.ApiResponse;
import marketservice.dto.CartAddRequest;
import marketservice.dto.CartItemResponse;
import marketservice.dto.CartUpdateRequest;
import marketservice.service.CartService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<List<CartItemResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(cartService.list(userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<List<CartItemResponse>> add(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody CartAddRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(cartService.add(userId, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PutMapping("/{productId}")
    public ApiResponse<List<CartItemResponse>> update(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long productId,
            @RequestBody CartUpdateRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(cartService.update(userId, productId, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<List<CartItemResponse>> remove(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long productId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(cartService.remove(userId, productId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }
}
