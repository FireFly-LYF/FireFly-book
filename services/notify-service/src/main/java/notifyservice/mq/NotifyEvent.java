package notifyservice.mq;

public class NotifyEvent {
    private Long userId;
    private Long fromUserId;
    private String type;
    private Long refId;
    private String content;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
