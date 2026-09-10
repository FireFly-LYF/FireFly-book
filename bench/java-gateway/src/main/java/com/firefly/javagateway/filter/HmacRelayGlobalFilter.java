package com.firefly.javagateway.filter;

import com.firefly.javagateway.config.GatewayProps;
import com.firefly.javagateway.security.HmacSupport;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 转发前清除客户端伪造身份头，注入与 Go 网关同款 HMAC。 */
@Component
public class HmacRelayGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayProps props;

    public HmacRelayGlobalFilter(GatewayProps props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path != null && (path.equals("/gateway/health") || path.startsWith("/actuator"))) {
            return chain.filter(exchange);
        }
        String secret = props.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            return chain.filter(exchange);
        }
        String userId = exchange.getAttributeOrDefault(JwtAuthGlobalFilter.ATTR_USER_ID, "");
        long ts = System.currentTimeMillis() / 1000L;
        String sign = HmacSupport.sign(secret, userId, ts);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("Authorization");
                    h.remove("X-User-Id");
                    h.remove("X-Gateway-Ts");
                    h.remove("X-Gateway-Sign");
                    if (userId != null && !userId.isBlank()) {
                        h.set("X-User-Id", userId);
                    }
                    h.set("X-Gateway-Ts", Long.toString(ts));
                    h.set("X-Gateway-Sign", sign);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
