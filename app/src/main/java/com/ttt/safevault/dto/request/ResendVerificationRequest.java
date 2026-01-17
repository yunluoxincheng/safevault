package com.ttt.safevault.dto.request;

/**
 * 重新发送验证邮件请求
 */
public class ResendVerificationRequest {
    private String email;

    public ResendVerificationRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
