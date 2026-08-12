package searchservice.mq;

/** 用户索引事件载荷（注册 / 改资料都发同一条 upsert） */
public class UserIndexEvent {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;

    public UserIndexEvent() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
