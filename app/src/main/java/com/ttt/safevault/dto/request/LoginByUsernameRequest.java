package com.ttt.safevault.dto.request;

/**
 * 通过用户名登录请求
 */
public class LoginByUsernameRequest {
    private String username;
    private String signature;
    private Long timestamp;

    public LoginByUsernameRequest() {
    }

    public LoginByUsernameRequest(String username, String signature, Long timestamp) {
        this.username = username;
        this.signature = signature;
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
