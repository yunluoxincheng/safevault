package com.ttt.safevault.dto.request;

/**
 * 用户注册请求DTO
 */
public class RegisterRequest {
    private String deviceId;
    private String username;
    private String displayName;
    private String publicKey;

    public RegisterRequest() {
    }

    public RegisterRequest(String deviceId, String username, String displayName, String publicKey) {
        this.deviceId = deviceId;
        this.username = username;
        this.displayName = displayName;
        this.publicKey = publicKey;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
