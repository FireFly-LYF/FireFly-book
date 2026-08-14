package com.firefly.internalauth;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/** 服务间直调补齐网关同款 HMAC，避免下游 Filter 拒绝。 */
public class GatewayHmacClientInterceptor implements ClientHttpRequestInterceptor {

    private final InternalAuthProperties props;

    public GatewayHmacClientInterceptor(InternalAuthProperties props) {
        this.props = props;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (props.isEnabled() && props.getHmacSecret() != null && !props.getHmacSecret().isBlank()) {
            String uid = request.getHeaders().getFirst("X-User-Id");
            GatewayHmacSupport.applyHeaders(request.getHeaders(), props.getHmacSecret(), uid);
        }
        return execution.execute(request, body);
    }
}
