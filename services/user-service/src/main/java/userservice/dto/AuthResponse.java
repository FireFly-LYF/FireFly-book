package userservice.dto;

import userservice.entity.User;

public class AuthResponse {
    /** 兼容旧前端：与 accessToken 相同 */
    private String token;
    private String accessToken;
    private String refreshToken;
    private User user;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, User user) {
        this.token = accessToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
