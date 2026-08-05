package userservice.controller;

import userservice.common.ApiResponse;
import userservice.dto.LoginRequest;
import userservice.dto.RegisterRequest;
import userservice.dto.UpdateProfileRequest;
import userservice.entity.User;
import userservice.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<User> register(@RequestBody RegisterRequest req) {
        try {
            User user = userService.register(
                    req.getUsername(), req.getPassword(), req.getNickname());
            return ApiResponse.ok(user);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<User> login(@RequestBody LoginRequest req) {
        User user = userService.login(req.getUsername(), req.getPassword());
        if (user == null) {
            return ApiResponse.fail(40101, "用户名或密码错误");
        }
        return ApiResponse.ok(user);
    }

    @GetMapping("/me")
    public ApiResponse<User> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        User u = userService.findById(userId);
        if (u == null) {
            return ApiResponse.fail(40401, "用户不存在");
        }
        return ApiResponse.ok(u);
    }

    @PutMapping("/me")
    public ApiResponse<User> updateMe(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody UpdateProfileRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
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
            return ApiResponse.fail(40401, "用户不存在");
        }
        return ApiResponse.ok(user);
    }
}
