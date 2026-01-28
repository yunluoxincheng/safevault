package com.ttt.safevault.dto.response;

import com.google.gson.annotations.SerializedName;

/**
 * 邮箱验证状态响应
 * 用于前端轮询检查验证是否完成
 */
public class VerificationStatusResponse {

    @SerializedName("status")
    private String status;

    @SerializedName("email")
    private String email;

    @SerializedName("username")
    private String username;

    @SerializedName("tokenExpiresAt")
    private String tokenExpiresAt;

    public VerificationStatusResponse() {
    }

    public VerificationStatusResponse(String status, String email, String username) {
        this.status = status;
        this.email = email;
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(String tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    /**
     * 是否已验证
     */
    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }

    /**
     * 是否待验证
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /**
     * 是否无记录
     */
    public boolean isNotFound() {
        return "NOT_FOUND".equals(status);
    }
}
