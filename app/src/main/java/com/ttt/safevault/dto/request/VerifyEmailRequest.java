package com.ttt.safevault.dto.request;

/**
 * 邮箱验证请求
 */
public class VerifyEmailRequest {
    private String token;

    public VerifyEmailRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
