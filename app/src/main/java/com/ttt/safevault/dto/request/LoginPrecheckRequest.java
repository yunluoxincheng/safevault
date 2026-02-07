package com.ttt.safevault.dto.request;

import com.google.gson.annotations.SerializedName;

/**
 * 登录预检查请求（Challenge-Response 机制）
 */
public class LoginPrecheckRequest {

    @SerializedName("email")
    private String email;

    public LoginPrecheckRequest() {
    }

    public LoginPrecheckRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
