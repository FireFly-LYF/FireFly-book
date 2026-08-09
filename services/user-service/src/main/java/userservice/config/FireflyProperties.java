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
        private String ttl = "24h";
        private String tenant = "tenant-a";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getTtl() { return ttl; }
        public void setTtl(String ttl) { this.ttl = ttl; }
        public String getTenant() { return tenant; }
        public void setTenant(String tenant) { this.tenant = tenant; }
    }
}
