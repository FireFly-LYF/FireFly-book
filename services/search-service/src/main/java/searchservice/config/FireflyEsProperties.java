package searchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对应 application.yml 里的 firefly.elasticsearch.* */
@ConfigurationProperties(prefix = "firefly.elasticsearch")
public class FireflyEsProperties {

    /** 例：http://127.0.0.1:9200 */
    private String baseUrl = "http://127.0.0.1:9200";
    private String noteIndex = "notes";
    private String userIndex = "users";
    /** 搜索关键词最大长度（防慢查询 / 滥用） */
    private int maxQueryLength = 64;
    /**
     * 启动时先删再建索引（换 IK mapping 时一次性打开）。
     * 会清空搜索副本，数据需靠 MQ / 业务重同步回填；平时保持 false。
     */
    private boolean recreateIndices = false;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getNoteIndex() { return noteIndex; }
    public void setNoteIndex(String noteIndex) { this.noteIndex = noteIndex; }
    public String getUserIndex() { return userIndex; }
    public void setUserIndex(String userIndex) { this.userIndex = userIndex; }
    public int getMaxQueryLength() { return maxQueryLength; }
    public void setMaxQueryLength(int maxQueryLength) { this.maxQueryLength = maxQueryLength; }
    public boolean isRecreateIndices() { return recreateIndices; }
    public void setRecreateIndices(boolean recreateIndices) { this.recreateIndices = recreateIndices; }
}
