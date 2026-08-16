package feedservice.mq;

/**
 * 关注事件：userId=被关注者(followee)，fromUserId=关注者(follower)。
 * 与 user-service NotifyEvent 对齐。
 */
public class FollowEvent {
    private Long userId;
    private Long fromUserId;
    private String type;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
