package com.firefly.internalauth;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 兼容旧探活路径 /health：返回与 /actuator/health 一致的 UP/DOWN（O2）。
 * 网关与 compose 可优先打 /actuator/health；/health 仍可用。
 */
@RestController
public class ActuatorHealthAliasController {

    private final HealthEndpoint healthEndpoint;

    public ActuatorHealthAliasController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        HealthComponent component = healthEndpoint.health();
        boolean up = Status.UP.equals(component.getStatus());
        return ResponseEntity
                .status(up ? 200 : 503)
                .body(Map.of("status", component.getStatus().getCode()));
    }
}
