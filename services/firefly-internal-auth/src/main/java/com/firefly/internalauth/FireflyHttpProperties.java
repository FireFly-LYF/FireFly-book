package com.firefly.internalauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 出站 HTTP（RestTemplate）超时 / 连接池 / 熔断。
 * 前缀：firefly.http
 */
@ConfigurationProperties(prefix = "firefly.http")
public class FireflyHttpProperties {

    /** 建立 TCP 连接超时 */
    private int connectTimeoutMs = 2000;
    /** 从连接池借连接超时 */
    private int connectionRequestTimeoutMs = 1000;
    /** 等待响应体超时（读超时） */
    private int readTimeoutMs = 3000;
    /** 连接池最大连接数 */
    private int maxTotal = 100;
    /** 每个路由（下游主机）最大连接数 */
    private int maxPerRoute = 20;
    /** 失败率达到该百分比则打开熔断 */
    private float circuitFailureRateThreshold = 50f;
    /** 熔断打开后等待多久进入半开 */
    private int circuitWaitOpenSeconds = 10;
    /** 滑动窗口调用次数 */
    private int circuitSlidingWindowSize = 20;
    /** 至少多少次调用后才计算失败率 */
    private int circuitMinimumNumberOfCalls = 10;

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getConnectionRequestTimeoutMs() { return connectionRequestTimeoutMs; }
    public void setConnectionRequestTimeoutMs(int connectionRequestTimeoutMs) {
        this.connectionRequestTimeoutMs = connectionRequestTimeoutMs;
    }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getMaxTotal() { return maxTotal; }
    public void setMaxTotal(int maxTotal) { this.maxTotal = maxTotal; }
    public int getMaxPerRoute() { return maxPerRoute; }
    public void setMaxPerRoute(int maxPerRoute) { this.maxPerRoute = maxPerRoute; }
    public float getCircuitFailureRateThreshold() { return circuitFailureRateThreshold; }
    public void setCircuitFailureRateThreshold(float circuitFailureRateThreshold) {
        this.circuitFailureRateThreshold = circuitFailureRateThreshold;
    }
    public int getCircuitWaitOpenSeconds() { return circuitWaitOpenSeconds; }
    public void setCircuitWaitOpenSeconds(int circuitWaitOpenSeconds) {
        this.circuitWaitOpenSeconds = circuitWaitOpenSeconds;
    }
    public int getCircuitSlidingWindowSize() { return circuitSlidingWindowSize; }
    public void setCircuitSlidingWindowSize(int circuitSlidingWindowSize) {
        this.circuitSlidingWindowSize = circuitSlidingWindowSize;
    }
    public int getCircuitMinimumNumberOfCalls() { return circuitMinimumNumberOfCalls; }
    public void setCircuitMinimumNumberOfCalls(int circuitMinimumNumberOfCalls) {
        this.circuitMinimumNumberOfCalls = circuitMinimumNumberOfCalls;
    }
}
