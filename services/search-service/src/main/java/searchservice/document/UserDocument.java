package searchservice.document;

/** 写入 ES users 索引的文档（按昵称/用户名搜） */
public class UserDocument {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;

    public UserDocument() {
    }

    public UserDocument(Long id, String username, String nickname, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
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
