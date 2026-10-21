package marketservice.controller;

import marketservice.common.ApiResponse;
import marketservice.dto.AddressRequest;
import marketservice.entity.Address;
import marketservice.service.AddressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<Address>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(addressService.list(userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Address> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody AddressRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(addressService.create(userId, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Address> update(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody AddressRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(addressService.update(userId, id, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            addressService.delete(userId, id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping("/{id}/default")
    public ApiResponse<Address> setDefault(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(addressService.setDefault(userId, id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }
}
