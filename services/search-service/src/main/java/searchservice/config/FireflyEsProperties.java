package searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对应 application.yml 里的 firefly.elasticsearch.* */
@ConfigurationProperties(prefix = "firefly.elasticsearch")
public class FireflyEsProperties {

    /** 例：http://127.0.0.1:9200 */
    private String baseUrl = "http://127.0.0.1:9200";
    private String noteIndex = "notes";
    private String userIndex = "users";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getNoteIndex() { return noteIndex; }
    public void setNoteIndex(String noteIndex) { this.noteIndex = noteIndex; }
    public String getUserIndex() { return userIndex; }
    public void setUserIndex(String userIndex) { this.userIndex = userIndex; }
}
