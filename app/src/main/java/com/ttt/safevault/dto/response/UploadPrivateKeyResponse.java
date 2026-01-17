package com.ttt.safevault.dto.response;

/**
 * 上传加密私钥响应
 */
public class UploadPrivateKeyResponse {

    private boolean success;
    private String version;
    private String uploadedAt;

    public UploadPrivateKeyResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
