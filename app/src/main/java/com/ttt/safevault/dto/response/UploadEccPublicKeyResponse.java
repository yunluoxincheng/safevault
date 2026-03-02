package com.ttt.safevault.dto.response;

/**
 * 上传 X25519/Ed25519 椭圆曲线公钥响应
 *
 * @since SafeVault 3.6.0
 */
public class UploadEccPublicKeyResponse {

    private boolean success;
    private String message;
    private long uploadedAt;

    public UploadEccPublicKeyResponse() {
    }

    public UploadEccPublicKeyResponse(boolean success, String message, long uploadedAt) {
        this.success = success;
        this.message = message;
        this.uploadedAt = uploadedAt;
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

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}