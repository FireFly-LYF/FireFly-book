package com.firefly.internalauth;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.slf4j.MDC;

import java.io.IOException;

/** 服务间直调补齐网关同款 HMAC，并透传 X-Request-Id。 */
public class GatewayHmacClientInterceptor implements ClientHttpRequestInterceptor {

    private final InternalAuthProperties props;

    public GatewayHmacClientInterceptor(InternalAuthProperties props) {
        this.props = props;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()
                && request.getHeaders().getFirst(RequestIdFilter.HEADER_REQUEST_ID) == null) {
            request.getHeaders().set(RequestIdFilter.HEADER_REQUEST_ID, requestId);
        }
        if (props.isEnabled() && props.getHmacSecret() != null && !props.getHmacSecret().isBlank()) {
            String uid = request.getHeaders().getFirst("X-User-Id");
            GatewayHmacSupport.applyHeaders(request.getHeaders(), props.getHmacSecret(), uid);
        }
        return execution.execute(request, body);
    }
}
