package com.firefly.helloservice.controller;

import com.firefly.helloservice.common.ApiResponse;
import com.firefly.helloservice.dto.RegisterRequest;
import com.firefly.helloservice.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
//表示这个类是一个控制器，且所有方法的返回值直接写入 HTTP 响应体
@RestController
//表示这个类处理所有以 /api/user 开头的请求
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    // 构造器注入（推荐写法）
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        try {
            Map<String, Object> user = userService.register(
                    req.getUsername(), req.getPassword(), req.getNickname());
            return ApiResponse.ok(user);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        Map<String, Object> user = userService.findById(id);
        if (user == null) {
            return ApiResponse.fail(40401, "用户不存在");
        }
        return ApiResponse.ok(user);
    }
}