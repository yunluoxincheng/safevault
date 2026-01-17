package com.ttt.safevault.dto.request;

/**
 * 邮箱注册请求（第一步）
 * 用户输入邮箱和用户名，系统发送验证邮件
 */
public class EmailRegistrationRequest {
    private String email;
    private String username;

    public EmailRegistrationRequest(String email, String username) {
        this.email = email;
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
