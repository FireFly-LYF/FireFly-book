package com.firefly.helloservice.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final AtomicLong seq = new AtomicLong(1);
    private final Map<String, Map<String, Object>> users = new HashMap<>();

    public Map<String, Object> register(String username, String password, String nickname) {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        Map<String, Object> u = new HashMap<>();
        u.put("id", seq.getAndIncrement());
        u.put("username", username);
        u.put("nickname", nickname);
        // 注意：真实项目密码要加密，这里仅演示
        u.put("password", password);
        users.put(username, u);

        Map<String, Object> safe = new HashMap<>();
        safe.put("id", u.get("id"));
        safe.put("username", username);
        safe.put("nickname", nickname);
        return safe;
    }

    public Map<String, Object> findById(Long id) {
        for (Map<String, Object> u : users.values()) {
            if (id.equals(u.get("id"))) {
                Map<String, Object> safe = new HashMap<>();
                safe.put("id", u.get("id"));
                safe.put("username", u.get("username"));
                safe.put("nickname", u.get("nickname"));
                return safe;
            }
        }
        return null;
    }
}