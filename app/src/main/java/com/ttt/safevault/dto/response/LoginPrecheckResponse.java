package com.ttt.safevault.dto.response;

import com.google.gson.annotations.SerializedName;

/**
 * 登录预检查响应（Challenge-Response 机制）
 */
public class LoginPrecheckResponse {

    @SerializedName("nonce")
    private String nonce;

    @SerializedName("expiresAt")
    private Long expiresAt;

    @SerializedName("userId")
    private String userId;

    public LoginPrecheckResponse() {
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
