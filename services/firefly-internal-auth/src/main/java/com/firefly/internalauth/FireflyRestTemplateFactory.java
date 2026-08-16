package com.firefly.internalauth;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 构建带「连接池 + 超时 + 熔断」的 RestTemplate。
 * 各服务 Bean 方法调用 {@link #create}，再按需追加 HMAC 等拦截器。
 */
public final class FireflyRestTemplateFactory {

    private FireflyRestTemplateFactory() {
    }

    /**
     * @param name            熔断器名称（如 feed-downstream / search-es）
     * @param props           超时与池参数
     * @param extraInterceptors 额外拦截器（如 HMAC）；熔断拦截器总会加在最后一层执行前
     */
    public static RestTemplate create(
            String name,
            FireflyHttpProperties props,
            ClientHttpRequestInterceptor... extraInterceptors) {
        FireflyHttpProperties p = props != null ? props : new FireflyHttpProperties();

        HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(Math.max(1, p.getMaxTotal()))
                .setMaxConnPerRoute(Math.max(1, p.getMaxPerRoute()))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(Math.max(1, p.getConnectTimeoutMs())))
                        .setSocketTimeout(Timeout.ofMilliseconds(Math.max(1, p.getReadTimeoutMs())))
                        .build())
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(
                        Timeout.ofMilliseconds(Math.max(1, p.getConnectionRequestTimeoutMs())))
                .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1, p.getReadTimeoutMs())))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate rt = new RestTemplate(factory);

        CircuitBreaker circuitBreaker = CircuitBreaker.of(name, circuitBreakerConfig(p));
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        if (extraInterceptors != null) {
            interceptors.addAll(Arrays.asList(extraInterceptors));
        }
        interceptors.add(new CircuitBreakerInterceptor(circuitBreaker));
        rt.setInterceptors(interceptors);
        return rt;
    }

    private static CircuitBreakerConfig circuitBreakerConfig(FireflyHttpProperties p) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(p.getCircuitFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(Math.max(1, p.getCircuitWaitOpenSeconds())))
                .slidingWindowSize(Math.max(1, p.getCircuitSlidingWindowSize()))
                .minimumNumberOfCalls(Math.max(1, p.getCircuitMinimumNumberOfCalls()))
                // 超时 / 5xx 计入失败；4xx 多为业务错误，不拉闸
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .build();
    }
}
