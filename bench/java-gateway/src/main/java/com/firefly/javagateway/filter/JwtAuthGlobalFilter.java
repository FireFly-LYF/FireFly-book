package com.firefly.javagateway.filter;

import com.firefly.javagateway.config.GatewayProps;
import com.firefly.javagateway.security.JwtSupport;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 校验（白名单外）。用户 id 放入 exchange attribute，供 HMAC 过滤器使用。
 * 对比实验：不做 Redis 限流，避免扭曲与 Go 容量轮的对比。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String ATTR_USER_ID = "firefly.userId";

    private final GatewayProps props;

    public JwtAuthGlobalFilter(GatewayProps props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        if (!props.isApiRequired()) {
            return chain.filter(exchange);
        }
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        String token = auth.substring("Bearer ".length()).trim();
        try {
            String userId = JwtSupport.parseUserId(props.getJwtSecret(), token);
            exchange.getAttributes().put(ATTR_USER_ID, userId == null ? "" : userId);
            return chain.filter(exchange);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    static boolean isWhitelisted(String path) {
        if (path == null) {
            return false;
        }
        if (path.equals("/gateway/health") || path.startsWith("/actuator")) {
            return true;
        }
        return path.equals("/api/user/login")
                || path.equals("/api/user/register")
                || path.equals("/api/user/refresh");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
