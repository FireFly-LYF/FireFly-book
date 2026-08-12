package userservice.mq;

/** 与 search-service 消费端字段保持一致 */
public class UserIndexEvent {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;

    public UserIndexEvent() {
    }

    public static UserIndexEvent from(Long id, String username, String nickname, String avatarUrl) {
        UserIndexEvent e = new UserIndexEvent();
        e.id = id;
        e.username = username;
        e.nickname = nickname;
        e.avatarUrl = avatarUrl;
        return e;
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
