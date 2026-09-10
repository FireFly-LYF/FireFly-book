package com.firefly.javagateway.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class HealthRoutes {

    @Bean
    public RouterFunction<ServerResponse> gatewayHealth() {
        return route(GET("/gateway/health"), request ->
                ServerResponse.ok().bodyValue(Map.of(
                        "code", 0,
                        "data", "java-gateway healthy",
                        "msg", "ok"
                )));
    }
}
