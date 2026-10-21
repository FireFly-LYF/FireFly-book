package marketservice.controller;

import marketservice.common.ApiResponse;
import marketservice.dto.CreateProductRequest;
import marketservice.dto.ProductDetailResponse;
import marketservice.dto.UpdateProductRequest;
import marketservice.entity.Product;
import marketservice.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productService.listOnSale(page, size));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> detail(@PathVariable Long id) {
        try {
            return ApiResponse.ok(productService.getDetail(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping("/products")
    public ApiResponse<Product> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateProductRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(productService.create(userId, req, idempotencyKey));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PutMapping("/products/{id}")
    public ApiResponse<Product> update(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody UpdateProductRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(productService.update(userId, id, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping("/products/{id}/off")
    public ApiResponse<Product> off(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        return setStatus(userId, id, Product.STATUS_OFF);
    }

    @PostMapping("/products/{id}/on")
    public ApiResponse<Product> on(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        return setStatus(userId, id, Product.STATUS_ON);
    }

    @GetMapping("/seller/products")
    public ApiResponse<List<Product>> sellerProducts(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(productService.listMine(userId, page, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    private ApiResponse<Product> setStatus(Long userId, Long id, String status) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(productService.setStatus(userId, id, status));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }
}
