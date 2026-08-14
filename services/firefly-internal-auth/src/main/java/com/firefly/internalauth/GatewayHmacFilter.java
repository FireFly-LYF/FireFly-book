package com.firefly.internalauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 校验网关注入的 X-Gateway-Ts / X-Gateway-Sign；拒绝伪造的 X-User-Id。
 * /health 放行；/files/** 跳过网关身份头（由 media SignedFileFilter 验签名）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GatewayHmacFilter extends OncePerRequestFilter {

    private final InternalAuthProperties props;

    public GatewayHmacFilter(InternalAuthProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        if ("/health".equals(path)) {
            return true;
        }
        return path.startsWith("/files/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String secret = props.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            writeUnauthorized(response, "internal auth not configured");
            return;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            userId = "";
        }
        String tsRaw = request.getHeader("X-Gateway-Ts");
        String sign = request.getHeader("X-Gateway-Sign");
        if (tsRaw == null || tsRaw.isBlank() || sign == null || sign.isBlank()) {
            writeUnauthorized(response, "missing gateway signature");
            return;
        }
        long ts;
        try {
            ts = Long.parseLong(tsRaw.trim());
        } catch (NumberFormatException e) {
            writeUnauthorized(response, "invalid gateway ts");
            return;
        }
        long now = java.time.Instant.now().getEpochSecond();
        long skew = Math.max(1, props.getSkewSec());
        if (Math.abs(now - ts) > skew) {
            writeUnauthorized(response, "gateway signature expired");
            return;
        }
        if (!GatewayHmacSupport.verify(secret, userId, sign.trim(), ts)) {
            writeUnauthorized(response, "invalid gateway signature");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
