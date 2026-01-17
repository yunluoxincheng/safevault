package com.ttt.safevault.dto.response;

/**
 * 移除设备响应
 */
public class RemoveDeviceResponse {

    private boolean success;
    private String message;
    private String removedDeviceId;

    public RemoveDeviceResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRemovedDeviceId() {
        return removedDeviceId;
    }

    public void setRemovedDeviceId(String removedDeviceId) {
        this.removedDeviceId = removedDeviceId;
    }
}
