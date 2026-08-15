package mediaservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    /** 本地/卷挂载目录；优先 MEDIA_STORAGE_DIR，否则见 application.yml */
    private String storageDir = System.getProperty("user.home") + "/FireFlyData/media";

    /**
     * 对外访问前缀。推荐经网关：http://127.0.0.1:8080/files
     * 入库只存相对路径 /files/{uuid}.ext；此前缀仅兼容旧逻辑。
     */
    private String publicBaseUrl = "http://127.0.0.1:8080/files";

    /** 签名密钥，必须由环境变量 MEDIA_SIGN_SECRET 注入 */
    private String signSecret;

    /** 签名 URL 有效期（秒） */
    private long signTtlSeconds = 3600;

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getSignSecret() {
        return signSecret;
    }

    public void setSignSecret(String signSecret) {
        this.signSecret = signSecret;
    }

    public long getSignTtlSeconds() {
        return signTtlSeconds;
    }

    public void setSignTtlSeconds(long signTtlSeconds) {
        this.signTtlSeconds = signTtlSeconds;
    }
}
