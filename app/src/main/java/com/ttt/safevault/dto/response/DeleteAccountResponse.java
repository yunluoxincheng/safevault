package com.ttt.safevault.dto.response;

import com.google.gson.annotations.SerializedName;

/**
 * 删除账户响应
 */
public class DeleteAccountResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    public DeleteAccountResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
