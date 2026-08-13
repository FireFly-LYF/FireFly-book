package userservice.dto;

public class LoginRequest {
    private String username;
    private String password;
    /** 客户端设备指纹（明文）；服务端入库前做 SHA-256 */
    private String deviceFingerprint;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
}
