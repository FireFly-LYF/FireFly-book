package userservice.controller;

import userservice.common.ApiResponse;
import userservice.dto.AuthResponse;
import userservice.dto.LoginRequest;
import userservice.dto.RefreshRequest;
import userservice.dto.RegisterRequest;
import userservice.dto.UpdateProfileRequest;
import userservice.entity.User;
import userservice.service.AuthTokenService;
import userservice.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final AuthTokenService authTokenService;

    public UserController(UserService userService, AuthTokenService authTokenService) {
        this.userService = userService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest req) {
        try {
            User user = userService.register(
                    req.getUsername(), req.getPassword(), req.getNickname());
            return ApiResponse.ok(authTokenService.issueTokens(user, req.getDeviceFingerprint()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest req) {
        try {
            User user = userService.login(req.getUsername(), req.getPassword());
            if (user == null) {
                return ApiResponse.fail(40101, "\u7528\u6237\u540d\u6216\u5bc6\u7801\u9519\u8bef");
            }
            return ApiResponse.ok(authTokenService.issueTokens(user, req.getDeviceFingerprint()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    /** refreshToken + device fingerprint -> new token pair */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshRequest req) {
        try {
            return ApiResponse.ok(authTokenService.refresh(
                    req.getRefreshToken(), req.getDeviceFingerprint()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40102, e.getMessage());
        }
    }

    /** revoke all refresh tokens; requires Access / X-User-Id */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "\u672a\u767b\u5f55");
        }
        authTokenService.logout(userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<User> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "\u672a\u767b\u5f55");
        }
        User u = userService.findById(userId);
        if (u == null) {
            return ApiResponse.fail(40401, "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        return ApiResponse.ok(u);
    }

    @PutMapping("/me")
    public ApiResponse<User> updateMe(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody UpdateProfileRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "\u672a\u767b\u5f55");
        }
        try {
            User u = userService.updateProfile(
                    userId, req.getNickname(), req.getAvatarUrl(), req.getBio());
            return ApiResponse.ok(u);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ApiResponse.fail(40401, "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        return ApiResponse.ok(user);
    }
}
