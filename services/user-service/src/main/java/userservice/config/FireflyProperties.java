package userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firefly")
public class FireflyProperties {

    private final Jwt jwt = new Jwt();

    public Jwt getJwt() {
        return jwt;
    }

    public static class Jwt {
        private String secret = "phase3-dev-secret-change-me-32bytes!";
        /** @deprecated 兼容旧配置；优先使用 accessTtl */
        private String ttl = "30m";
        private String accessTtl = "30m";
        private String refreshTtl = "14d";
        private String tenant = "tenant-a";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getTtl() { return ttl; }
        public void setTtl(String ttl) { this.ttl = ttl; }
        public String getAccessTtl() { return accessTtl; }
        public void setAccessTtl(String accessTtl) { this.accessTtl = accessTtl; }
        public String getRefreshTtl() { return refreshTtl; }
        public void setRefreshTtl(String refreshTtl) { this.refreshTtl = refreshTtl; }
        public String getTenant() { return tenant; }
        public void setTenant(String tenant) { this.tenant = tenant; }

        /** 实际 Access TTL：access-ttl 优先，否则回退 ttl */
        public String resolveAccessTtl() {
            if (accessTtl != null && !accessTtl.isBlank()) {
                return accessTtl;
            }
            return ttl;
        }
    }
}
