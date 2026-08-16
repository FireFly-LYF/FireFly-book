package com.firefly.internalauth;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

/** 将 RestTemplate 调用包进 Resilience4j CircuitBreaker；熔断打开时快速失败。 */
public class CircuitBreakerInterceptor implements ClientHttpRequestInterceptor {

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerInterceptor(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            return circuitBreaker.executeCheckedSupplier(() -> execution.execute(request, body));
        } catch (CallNotPermittedException e) {
            // ResourceAccessException 只接受 IOException cause；熔断用文案区分即可
            throw new ResourceAccessException("circuit-breaker open: " + circuitBreaker.getName());
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new ResourceAccessException("downstream call failed: " + e.getMessage());
        }
    }
}
