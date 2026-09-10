package com.firefly.javagateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firefly.gateway")
public class GatewayProps {

    /** 与 user-service / Go Gateway 相同的 JWT_SECRET */
    private String jwtSecret = "";
    /** 与各服务 INTERNAL_HMAC_SECRET 一致 */
    private String hmacSecret = "";
    /** 是否强制业务 API 带 JWT（对比实验默认 true） */
    private boolean apiRequired = true;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public boolean isApiRequired() {
        return apiRequired;
    }

    public void setApiRequired(boolean apiRequired) {
        this.apiRequired = apiRequired;
    }
}
