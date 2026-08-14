package com.firefly.internalauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firefly.internal-auth")
public class InternalAuthProperties {

    private boolean enabled = true;
    private String hmacSecret = "firefly-internal-hmac-dev-change-me!";
    private long skewSec = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
    public long getSkewSec() { return skewSec; }
    public void setSkewSec(long skewSec) { this.skewSec = skewSec; }
}
