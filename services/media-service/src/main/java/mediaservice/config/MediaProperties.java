package mediaservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    /** 本地存储目录，如 D:/FireFlyData/media */
    private String storageDir = "D:/A_Software/Java/SAVE/FireFly-book/save/media";

    /** 对外访问前缀，如 http://127.0.0.1:9003/files */
    private String publicBaseUrl = "http://127.0.0.1:9003/files";

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
}
